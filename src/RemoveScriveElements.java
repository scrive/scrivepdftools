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
import java.io.OutputStream;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.io.RandomAccessSource;
import com.itextpdf.text.io.RandomAccessSourceFactory;
import com.itextpdf.text.pdf.PRTokeniser;
import com.itextpdf.text.pdf.PdfContentParser;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfObject;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.RandomAccessFileOrArray;

public class RemoveScriveElements extends Engine {

    YamlSpec spec = null;

    public void Init(InputStream specFile, String inputOverride, String outputOverride) throws IOException {
        spec = YamlSpec.loadFromStream(specFile, YamlSpec.class);
        if (inputOverride != null) {
            spec.input = inputOverride;
        }
        if (outputOverride != null) {
            spec.output = outputOverride;
        }
    }
    
    public void execute(InputStream pdf, OutputStream os) throws IOException, DocumentException {
        if (pdf == null)
            pdf = new FileInputStream(spec.input);
        if ((os == null) && (spec.output != null))
            os = new FileOutputStream(spec.output);
        if (os == null)
            return; // no result expected, cancel
        PdfReader reader = new PdfReader(pdf);
        PdfStamper stamper = new PdfStamper(reader, os);

        // TODO: remove verification pages, background and footers

        stamper.close();
        reader.close();
        pdf.close();
    }
}
