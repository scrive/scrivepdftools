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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;

class SelectAndClipSpec extends YamlSpec
{
    public ArrayList<Integer> pages; // pages to search, 1-based
    public ArrayList<Double> clip;   // pages to search, 1-based
}


public class SelectAndClip extends Engine {

    SelectAndClipSpec spec = null;
    
    public void Init(InputStream specFile) throws IOException {
        spec = SelectAndClipSpec.loadFromStream(specFile, SelectAndClipSpec.class);
    }

    public void execute(InputStream is, OutputStream os)
        throws IOException, DocumentException
    {
        if (is == null)
            is = new FileInputStream(spec.input);
        if (os == null)
            os = new FileOutputStream(spec.output);
        PdfReader reader = new PdfReader(is);
        Document document = new Document();
        PdfWriter writer = PdfWriter.getInstance(document, os);
        document.open();

        int count = reader.getNumberOfPages();
        float clipRectX = (float)(double)spec.clip.get(0);
        float clipRectY = (float)(double)spec.clip.get(1);
        float clipRectW = (float)(double)spec.clip.get(2) - clipRectX;
        float clipRectH = (float)(double)spec.clip.get(3) - clipRectY;

        for(Integer page: spec.pages) {
            int p = page;
            if( p>=1 && p <=count ) {
                Rectangle pagesize = reader.getPageSizeWithRotation(1);
                document.setPageSize(pagesize);
                document.newPage();

                PdfContentByte content = writer.getDirectContent();

                PdfImportedPage ipage = writer.getImportedPage(reader, p);
                // for some reason using Rectangle here does not work...
                content.rectangle(clipRectX, clipRectY, clipRectW, clipRectH);
                content.clip();
                // content.stroke();
                content.newPath();
                content.addTemplate(ipage,0,0);
            }
        }

        writer.freeReader(reader);
        reader.close();
        document.close();
        writer.close();
    }
    
}
