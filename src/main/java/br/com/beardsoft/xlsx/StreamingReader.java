package br.com.beardsoft.xlsx;

import static br.com.beardsoft.xlsx.XmlUtils.document;
import static br.com.beardsoft.xlsx.XmlUtils.searchForNodeList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import br.com.beardsoft.xlsx.exceptions.CloseException;
import br.com.beardsoft.xlsx.exceptions.MissingSheetException;
import br.com.beardsoft.xlsx.exceptions.OpenException;
import br.com.beardsoft.xlsx.exceptions.ReadException;
import br.com.beardsoft.xlsx.impl.StreamingCell;
import br.com.beardsoft.xlsx.impl.StreamingRow;

/**
 * Streaming Excel workbook implementation. Most advanced features of POI are not supported.
 * Use this only if your application can handle iterating through an entire workbook, row by
 * row.
 */
public class StreamingReader implements Iterable<Row> {
  private static final Logger log = LoggerFactory.getLogger(StreamingReader.class);

  private SharedStringsTable sst;
  private XMLEventReader parser;
  private String lastContents;
  private boolean nextIsString;

  private int rowCacheSize;
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private List<Row> rowCache = new ArrayList();
  private Iterator<Row> rowCacheIterator;
  private StreamingRow currentRow;
  private StreamingCell currentCell;

  private File tmp;

  private static String currentSheetName;

  private StreamingReader(SharedStringsTable sst, XMLEventReader parser, int rowCacheSize) {
    this.sst = sst;
    this.parser = parser;
    this.rowCacheSize = rowCacheSize;
  }

  /**
   * Read through a number of rows equal to the rowCacheSize field or until there is no more data to read
   *
   * @return
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  private boolean getRow() {
    try {
      int iters = 0;
      rowCache = new ArrayList();
      while (rowCache.size() < rowCacheSize && parser.hasNext()) {
        handleEvent(parser.nextEvent());
        iters++;
      }
      rowCache.add(currentRow);
      rowCacheIterator = rowCache.iterator();
      return iters > 0;
    } catch (XMLStreamException e) {
      log.debug("End of stream");
    } catch (SAXException e) {
    	log.debug("End of stream");
	}
    return false;
  }

  public String getSheetName() {
	  return currentSheetName;
  }

  /**
   * Handles a SAX event.
   *
   * @param event
   * @throws SAXException
   */
  private void handleEvent(XMLEvent event) throws SAXException {
    if (event.getEventType() == XMLStreamConstants.CHARACTERS) {
      Characters c = event.asCharacters();
      lastContents += c.getData();
    } else if (event.getEventType() == XMLStreamConstants.START_ELEMENT) {
      StartElement startElement = event.asStartElement();
      if (startElement.getName().getLocalPart().equals("c")) {
        Attribute ref = startElement.getAttributeByName(new QName("r"));

        String[] coord = ref.getValue().split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
        StreamingCell cc = new StreamingCell(CellReference.convertColStringToIndex(coord[0]), Integer.parseInt(coord[1]) - 1);

        if (currentCell == null || currentCell.getRowIndex() != cc.getRowIndex()) {
          if (currentRow != null) {
            rowCache.add(currentRow);
          }
          currentRow = new StreamingRow(cc.getRowIndex());
        }
        currentCell = cc;

        Attribute type = startElement.getAttributeByName(new QName("t"));
        String cellType = type == null ? null : type.getValue();
        nextIsString = cellType != null && cellType.equals("s");
      }
      // Clear contents cache
      lastContents = "";
    } else if (event.getEventType() == XMLStreamConstants.END_ELEMENT) {
      EndElement endElement = event.asEndElement();
      if (nextIsString) {
        int idx = Integer.parseInt(lastContents);
        lastContents = new XSSFRichTextString(sst.getEntryAt(idx)).toString();
        nextIsString = false;
      }

      if (endElement.getName().getLocalPart().equals("v")) {
        currentCell.setContents(lastContents);
        currentRow.getCellMap().put(currentCell.getColumnIndex(), currentCell);
      }

    }
  }

  /**
   * Returns a new streaming iterator to loop through rows. This iterator is not
   * guaranteed to have all rows in memory, and any particular iteration may
   * trigger a load from disk to read in new data.
   *
   * @return the streaming iterator
   */
  @Override
  public Iterator<Row> iterator() {
    return new StreamingIterator();
  }

  /**
   * Closes the streaming resource, attempting to clean up any temporary files created.
   *
   * @throws com.monitorjbl.xlsx.exceptions.CloseException if there is an issue closing the stream
   */
  public void close() {
    try {
      parser.close();
    } catch (XMLStreamException e) {
      throw new CloseException(e);
    }

    if (tmp != null) {
      log.debug("Deleting tmp file [" + tmp.getAbsolutePath() + "]");
      tmp.delete();
    }
  }

  static File writeInputStreamToFile(InputStream is, int bufferSize) throws IOException {
	  File f = File.createTempFile("tmp-", ".xlsx");
      FileOutputStream fos = new FileOutputStream(f);
      int read;
      byte[] bytes = new byte[bufferSize];
      while ((read = is.read(bytes)) != -1) {
        fos.write(bytes, 0, read);
      }
      is.close();
      fos.close();
      return f;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    int rowCacheSize = 10;
    int bufferSize = 1024;
    int sheetIndex = 0;
    String sheetName;

    /**
     * The number of rows to keep in memory at any given point.
     * <p>
     * Defaults to 10
     * </p>
     *
     * @param rowCacheSize number of rows
     * @return reference to current {@code Builder}
     */
    public Builder rowCacheSize(int rowCacheSize) {
      this.rowCacheSize = rowCacheSize;
      return this;
    }

    /**
     * The number of bytes to read into memory from the input
     * resource.
     * <p>
     * Defaults to 1024
     * </p>
     *
     * @param bufferSize buffer size in bytes
     * @return reference to current {@code Builder}
     */
    public Builder bufferSize(int bufferSize) {
      this.bufferSize = bufferSize;
      return this;
    }

    /**
     * Which sheet to open. There can only be one sheet open
     * for a single instance of {@code StreamingReader}. If
     * more sheets need to be read, a new instance must be
     * created.
     * <p>
     * Defaults to 0
     * </p>
     *
     * @param sheetIndex index of sheet
     * @return reference to current {@code Builder}
     */
    public Builder sheetIndex(int sheetIndex) {
      this.sheetIndex = sheetIndex;
      return this;
    }

    /**
     * Which sheet to open. There can only be one sheet open
     * for a single instance of {@code StreamingReader}. If
     * more sheets need to be read, a new instance must be
     * created.
     *
     * @param sheetName name of sheet
     * @return reference to current {@code Builder}
     */
    public Builder sheetName(String sheetName) {
      this.sheetName = sheetName;
      return this;
    }

    /**
     * Reads a given {@code InputStream} and returns a new
     * instance of {@code StreamingReader}. Due to Apache POI
     * limitations, a temporary file must be written in order
     * to create a streaming iterator. This process will use
     * the same buffer size as specified in {@link #bufferSize(int)}.
     *
     * @param is input stream to read in
     * @return built streaming reader instance
     * @throws com.monitorjbl.xlsx.exceptions.ReadException if there is an issue reading the stream
     */
    public StreamingReader read(InputStream is) {
      File f = null;
      try {
        f = writeInputStreamToFile(is, bufferSize);
        log.debug("Created temp file [" + f.getAbsolutePath() + "]");

        StreamingReader r = read(f);
        r.tmp = f;
        return r;
      } catch (IOException e) {
        throw new ReadException("Unable to read input stream", e);
      } catch (RuntimeException e) {
        f.delete();
        throw e;
      }
    }

    /**
     * Reads a given {@code File} and returns a new instance
     * of {@code StreamingReader}.
     *
     * @param f file to read in
     * @return built streaming reader instance
     * @throws com.monitorjbl.xlsx.exceptions.OpenException if there is an issue opening the file
     * @throws com.monitorjbl.xlsx.exceptions.ReadException if there is an issue reading the file
     */
    public StreamingReader read(File f) {
      try {
        OPCPackage pkg = OPCPackage.open(f);
        XSSFReader reader = new XSSFReader(pkg);
        SharedStringsTable sst = reader.getSharedStringsTable();

        InputStream sheet = findSheet(reader);
        if (sheet == null) {
          throw new MissingSheetException("Unable to find sheet at index [" + sheetIndex + "]");
        }

        XMLEventReader parser = XMLInputFactory.newInstance().createXMLEventReader(sheet);
        return new StreamingReader(sst, parser, rowCacheSize);
      } catch (IOException e) {
        throw new OpenException("Failed to open file", e);
      } catch (OpenXML4JException e) {
        throw new ReadException("Unable to read workbook", e);
      } catch (XMLStreamException e) {
    	  throw new ReadException("Unable to read workbook", e);
      }
    }

    InputStream findSheet(XSSFReader reader) throws IOException, InvalidFormatException {
      int index = sheetIndex;

      //This file is separate from the worksheet data, and should be fairly small
      NodeList nl = searchForNodeList(document(reader.getWorkbookData()), "/workbook/sheets/sheet");

      if (sheetName != null) {
        index = -1;

        for (int i = 0; i < nl.getLength(); i++) {
          if (nl.item(i).getAttributes().getNamedItem("name").getTextContent().equals(sheetName)) {
            index = i;
          }
        }
        if (index < 0) {
          return null;
        }
      }
      Iterator<InputStream> iter = reader.getSheetsData();
      InputStream sheet = null;

      int i = 0;
      while (iter.hasNext()) {
        InputStream is = iter.next();
        if (i == index) {
          currentSheetName = nl.item(i).getAttributes().getNamedItem("name").getTextContent();
          sheet = is;
          log.debug("Found sheet at index [" + sheetIndex + "]");
          break;
        }
        i++;
      }
      return sheet;
    }
  }

  class StreamingIterator implements Iterator<Row> {
    @Override
    public boolean hasNext() {
      return (rowCacheIterator != null && rowCacheIterator.hasNext()) || getRow();
    }

    @Override
    public Row next() {
      return rowCacheIterator.next();
    }

    @Override
    public void remove() {
      throw new RuntimeException("NotSupported");
    }
  }

}