import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.itextpdf.text.DocumentException;

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

public abstract class Engine {
    

    /**
     * Initializes engine specification.
     * @param spec
     * @param inputOverride
     * @param outputOverride
     * @throws IOException
     */
    public abstract void Init(InputStream spec, String inputOverride, String outputOverride) throws IOException;

    /**
     * This version works on external disk files. 
     * @param specFile      
     * @param inputOverride
     * @param outputOverride
     * @throws IOException
     * @throws DocumentException
     */
    public void execute(String specFile, String inputOverride, String outputOverride) throws IOException, DocumentException {
        FileInputStream spec = new FileInputStream(specFile);  
        Init(spec, inputOverride, outputOverride);
        execute((InputStream)null, (OutputStream)null);
        spec.close();
    }
  
    
    /**
     * This version works on raw data.
     * @param config
     * @param pdf
     * @return
     * @throws IOException
     * @throws DocumentException
     */
    public abstract void execute(InputStream pdf, OutputStream out) throws IOException, DocumentException;

}
