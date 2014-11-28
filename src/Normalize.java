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
import java.io.File;

import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.Constructor;

import com.itextpdf.text.DocWriter;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfArray;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfObject;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfStream;

class NormalizeSpec
{
    public String input;
    public String output;

    /*
     * YAML is compatible with JSON (at least with the JSON we generate).
     *
     * It was the simplest method to read in JSON values.
     */
    public static Yaml getYaml() {
        Constructor constructor = new Constructor(NormalizeSpec.class);
        constructor.getPropertyUtils().setSkipMissingProperties(true);

        Yaml yaml = new Yaml(constructor);
        return yaml;
    }

    public static NormalizeSpec loadFromFile(String fileName) throws IOException {
        InputStream input = new FileInputStream(new File(fileName));
        Yaml yaml = getYaml();
        return (NormalizeSpec)yaml.load(input);
    }
}



public class Normalize {

    public static void execute(String specFile, String inputOverride)
        throws IOException, DocumentException
    {
        NormalizeSpec spec = NormalizeSpec.loadFromFile(specFile);
        if( inputOverride!=null ) {
            spec.input = inputOverride;
        }

        /*
          DumperOptions options = new DumperOptions();
          Yaml yaml = new Yaml(options);
          options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
          System.out.println(yaml.dump(spec));
        */
        execute(spec);

    }

    /**
     * This method detects text direction and 'Rotate' flag on each page and applies auto-rotation to output horizontal text. 
     * 'Rotate' flag is removed.
     *  
     * @param reader - input PDF
     * @param stamper - output writer
     */
    private static void unrotatePages( PdfReader reader, PdfStamper stamper) throws IOException {
        final int n = reader.getNumberOfPages(); 
        for (int i = 1; i <= n; i++) {
            PdfDictionary page = reader.getPageN(i);
            final int rot = ExtractTexts.detectPageRotation(reader, i);
            page.put(PdfName.ROTATE, null);
            PdfObject content = PdfReader.getPdfObject(page.get(PdfName.CONTENTS), page);
            PdfArray ar = null;
            if (content == null)
            	continue;
            else if (content.isStream()) {
            	ar = new PdfArray();
            	ar.add(page.get(PdfName.CONTENTS));
                page.put(PdfName.CONTENTS, ar);
            } else if (content.isArray()) {
            	ar = (PdfArray)content;
            }
            String ctm0 = null;
            PdfArray media = page.getAsArray(PdfName.MEDIABOX);
            Rectangle bbox1 = null;
            final float m[] = {media.getAsNumber(0).floatValue(), media.getAsNumber(1).floatValue(), media.getAsNumber(2).floatValue(), media.getAsNumber(3).floatValue()};
            final float x0 = Math.min(m[0], m[2]), y0 = Math.min(m[1], m[3]); 
            final float w0 = Math.abs(m[2] - m[0]), h0 = Math.abs(m[3] - m[1]); 
            switch (rot) {
            	case 90:
            		ctm0 = "0 -1 1 0 " + (-y0) + " " + (-x0 + w0) + " cm\n";
            		bbox1 = new Rectangle(0, 0, h0, w0);
            		break;
        		case 180:
        			ctm0 = "-1 0 0 -1 " + (x0 + w0) + " " + (y0 + h0) + " cm\n";
        			break; 
        		case 270: ctm0 = "0 1 -1 0 " + (y0 + h0) + " " + (-x0) + " cm\n";
        			bbox1 = new Rectangle(0, 0, h0, w0);
        			break;
        		case 0:
        		default: continue;  
            }
            if (bbox1 != null) {
            	float bb1[] = {bbox1.getLeft(), bbox1.getBottom(), bbox1.getRight(), bbox1.getTop()};
                page.put(PdfName.MEDIABOX, new PdfArray(bb1));
                page.put(PdfName.CROPBOX, null); // TODO: calculate CORRECT cropbox !
            }
            PdfStream stream2 = new PdfStream(DocWriter.getISOBytes(ctm0));
            ar.addFirst(stamper.getWriter().addToBody(stream2).getIndirectReference()); 
            stream2.flateCompress(PdfStream.DEFAULT_COMPRESSION);
        }
    }
    
    public static void execute(NormalizeSpec spec)
        throws IOException, DocumentException
    {
        PdfReader reader = new PdfReader(spec.input);
        FileOutputStream os = new FileOutputStream(spec.output);
        PdfStamper stamper = new PdfStamper(reader, os);

        stamper.setFormFlattening(true);
        stamper.setFreeTextFlattening(true);
        unrotatePages(reader, stamper);
        
        stamper.close();
        reader.close();
    }
}
