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

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.net.URL;
import java.net.MalformedURLException;
import java.lang.Character.UnicodeBlock;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.Constructor;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.BadElementException;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfSmartCopy;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.Font;
import com.itextpdf.text.Font.FontFamily;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.CMYKColor;
import com.itextpdf.text.pdf.PdfPTableEvent;

class SelectAndClipSpec
{
    public String input;
    public String output;

    public ArrayList<Integer> pages; // pages to search, 1-based
    public ArrayList<Double> clip;   // pages to search, 1-based

    /*
     * YAML is compatible with JSON (at least with the JSON we generate).
     *
     * It was the simplest method to read in JSON values.
     */
    public static Yaml getYaml() {
        Constructor constructor = new Constructor(SelectAndClipSpec.class);
        constructor.getPropertyUtils().setSkipMissingProperties(true);

        /*
         * Java reflection is missing some crucial information about
         * elements of containers.  Add this information here.
         */
        TypeDescription selectAndClipSpecDesc = new TypeDescription(SelectAndClipSpec.class);
        constructor.addTypeDescription(selectAndClipSpecDesc);

        Yaml yaml = new Yaml(constructor);
        return yaml;
    }

    public static SelectAndClipSpec loadFromFile(String fileName) throws IOException {
        InputStream input = new FileInputStream(new File(fileName));
        Yaml yaml = getYaml();
        return (SelectAndClipSpec)yaml.load(input);
    }
}


public class SelectAndClip {

    public static void execute(SelectAndClipSpec spec)
        throws IOException, DocumentException
    {
        FileOutputStream os = new FileOutputStream(spec.output);
        FileInputStream is = new FileInputStream(spec.input);
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


    public static void execute(String specFile, String inputOverride, String outputOverride)
        throws IOException, DocumentException
    {
        SelectAndClipSpec spec = SelectAndClipSpec.loadFromFile(specFile);
        if( inputOverride!=null ) {
            spec.input = inputOverride;
        }
        if( outputOverride!=null ) {
            spec.output = outputOverride;
        }

        /*
          DumperOptions options = new DumperOptions();
          Yaml yaml = new Yaml(options);
          options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
          System.out.println(yaml.dump(spec));
        */
        execute(spec);

    }
}
