/*
 *  Copyright (C) 2014 Scrive AB
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;

import com.itextpdf.text.DocWriter;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfArray;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfLiteral;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfString;
import com.itextpdf.text.pdf.PdfObject;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfStream;
import com.itextpdf.text.pdf.parser.ContentByteUtils;
import com.itextpdf.text.pdf.parser.ContentOperator;
import com.itextpdf.text.pdf.parser.ImageRenderInfo;
import com.itextpdf.text.pdf.parser.PdfContentStreamProcessor;
import com.itextpdf.text.pdf.parser.RenderListener;
import com.itextpdf.text.pdf.parser.TextRenderInfo;

public class RemoveJavaScript extends Engine
{
    YamlSpec spec = null;

    public void Init(InputStream specFile) throws IOException {
        spec = YamlSpec.loadFromStream(specFile, YamlSpec.class);
    }

    public static void traversePdfDictionary(final PdfDictionary dict)
    {
        for (Object key : dict.getKeys()) {
            Object data = dict.get((PdfName) key);

            if (key.toString().equals("/JS")) {
                // We change JS actions to empty actions.
                dict.put((PdfName) key, new PdfString(""));
            }
            else if (data instanceof PdfDictionary) {
                traversePdfDictionary((PdfDictionary)data);
            }
            else if ( data instanceof PdfArray) {
                traversePdfArray((PdfArray)data);
            }
        }
    }

    public static void traversePdfArray(final PdfArray array)
    {
        final Iterator iter = array.listIterator();
        while (iter.hasNext()) {
            Object data = iter.next();

            if (data instanceof PdfDictionary) {
                traversePdfDictionary((PdfDictionary)data);
            } else if (data instanceof PdfArray) {
                traversePdfArray((PdfArray)data);
            }
        }
    }

    public void execute(InputStream pdf, OutputStream os) throws IOException, DocumentException {
        if (pdf == null)
            pdf = new FileInputStream(spec.input);
        if ((os == null) && (spec.output != null))
            os = new FileOutputStream(spec.output);
        PdfReader reader = new PdfReader(pdf);
        ByteArrayOutputStream buf = null;
        if (os == null) {
            return; // no result expected, cancel
        }

        // Remove document level JavaScript.
        //
        // To support the use of parameterized function calls in
        // JavaScript scripts, the JavaScript entry in a PDF
        // document’s name dictionary (see Section 3.6.3, “Name
        // Dictionary”) can contain a name tree that maps name strings
        // to document-level JavaScript actions. When the document is
        // opened, all of the actions in this name tree are executed,
        // defining JavaScript functions for use by other scripts in
        // the document.
        PdfDictionary dict = reader.getCatalog().getAsDict( PdfName.NAMES );
        if( dict!=null ) {
            dict.remove( PdfName.JAVASCRIPT );
        }

        // Iterate on the indirect references by number
        int numObjs = reader.getXrefSize();
        for (int i = 0; i < numObjs; ++i) {
            final PdfObject curObj = reader.getPdfObject(i);


            if (curObj instanceof PdfDictionary) {
                traversePdfDictionary((PdfDictionary) curObj);
            } else if (curObj instanceof PdfArray) {
                traversePdfArray((PdfArray) curObj);
            }

        }

        PdfStamper stamper = new PdfStamper(reader, os);

        stamper.close();
        reader.close();
        pdf.close();
    }
}
