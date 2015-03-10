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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;


abstract class TextEngine extends Engine {

    protected PdfStamper stamper = null;
    protected TextDump text = null;

    /**
     * Derived class should use this method to provide it's spec
     * @return
     */
    public abstract YamlSpec getSpec();

    /**
     * Derived class should use this method to provide it's stamped output path (null = no stamping)
     * @return
     */
    public abstract String   getStampedOutput();
   
    /**
     * Main proc of a text engine
     * @param out
     */
    public abstract void execute(OutputStream out) throws IOException, DocumentException;

    public void execute(InputStream pdf, OutputStream out) throws IOException, DocumentException
    {
        YamlSpec spec = getSpec();
        text = null;
        if (spec.dumpPath != null) {
            try {
                text = TextDump.load(new FileInputStream(spec.dumpPath));
            } catch (ClassNotFoundException e) {
                e.printStackTrace(System.err);
            }
        }
        PdfReader reader = null;
        stamper = null;        
        if ((getStampedOutput() != null) || (text == null)) {
            if ((pdf == null) && (spec.input != null)) {
                pdf = new FileInputStream(spec.input);
            }
            if (text == null) {
                reader = TextDump.createFlattened(new PdfReader(pdf));
                text = new TextDump(reader);
            } else
                reader = new PdfReader(pdf);
            if (getStampedOutput() != null) {
                stamper = new PdfStamper(reader, new FileOutputStream(getStampedOutput()));
            }
        }
        
        execute(out);

        // close streams
        if( stamper!=null ) {
            stamper.close();
        }
        if ( reader!=null ) {
            reader.close();
        }
        if ( pdf != null ) {
            pdf.close();
        }
        
    }
}