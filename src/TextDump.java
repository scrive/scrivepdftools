/*
 *  Copyright (C) 2015 Scrive AB
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Set;
import java.util.TreeSet;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;


/**
 * Obtains and stores text for the whole PDF (multipage document)
 *
 */
public class TextDump implements Serializable
{
    private static final long serialVersionUID = 2556750841442970184L;

    public PdfAdditionalInfo info = null;
    PageText text[] = null;
    
    public static PdfReader createFlattened(PdfReader reader) throws IOException, DocumentException {
        // This is here to flatten forms so that texts in them can be read
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PdfStamper stamper = new PdfStamper(reader, buf);
        AcroFields fields = stamper.getAcroFields();
        Set<String> fieldNames = new TreeSet<String>(fields.getFields().keySet());
        // copy collection of keys, because we will modify while traversing
        for (String key : fieldNames) {
            if (key.contains("\\") || key.contains("#")) {
                // such keys break xml parser anyway
                fields.removeField(key);
            }
        }
        stamper.setFormFlattening(true);
        stamper.setFreeTextFlattening(true);
        stamper.close();
        return new PdfReader(buf.toByteArray());
    }

    public TextDump(PdfReader reader) throws IOException {
        
        info = new PdfAdditionalInfo();
        info.numberOfPages = reader.getNumberOfPages();
        text = new PageText[info.numberOfPages];
        for (int i = 1; i <= info.numberOfPages; i++) {
            text[i - 1] = new PageText(reader, i);
            info.containsControlCodes = info.containsControlCodes || text[i - 1].containsControlCodes();
            info.containsGlyphs = info.containsGlyphs || text[i - 1].containsGlyphs();
        }
        info.firstPageWidth = text[0].pageSizeRotated.getWidth();
        info.firstPageHeight = text[0].pageSizeRotated.getHeight();
    }
    
    static TextDump load(InputStream in) throws IOException, ClassNotFoundException {
        ObjectInputStream dump = new ObjectInputStream(in);
        TextDump text = null;
        text = (TextDump)dump.readObject();
        dump.close();
        return text;
    }

}