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

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;

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

    public static void execute(NormalizeSpec spec)
        throws IOException, DocumentException
    {
        PdfReader reader = new PdfReader(spec.input);
        FileOutputStream os = new FileOutputStream(spec.output);
        PdfStamper stamper = new PdfStamper(reader, os);

        stamper.setFormFlattening(true);
        stamper.setFreeTextFlattening(true);

        stamper.close();
        reader.close();
    }
}
