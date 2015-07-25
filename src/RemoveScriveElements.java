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
import java.util.Collections;
import java.util.HashMap;
import java.util.Stack;

import org.bouncycastle.util.Arrays;
import org.yaml.snakeyaml.TypeDescription;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfStream;

class RemoveScriveElementsSpec extends YamlSpec
{
    public Boolean removeFooter = false;
    public Boolean removeVerificationPages = false;
    public Boolean removeBackground = false;
    public Boolean removeEvidenceAttachments = false;

    static private ArrayList<TypeDescription> td = null;

    static public ArrayList<TypeDescription> getTypeDescriptors() {
        if (td == null) {
            td = new ArrayList<TypeDescription>();
            td.add(new TypeDescription(RemoveScriveElementsSpec.class));
        }
        return td;
    }
}

public class RemoveScriveElements extends Engine {

    RemoveScriveElementsSpec spec = null;

    public void Init(InputStream specFile, String inputOverride, String outputOverride) throws IOException {
        YamlSpec.setTypeDescriptors(RemoveScriveElementsSpec.class, RemoveScriveElementsSpec.getTypeDescriptors());
        spec = YamlSpec.loadFromStream(specFile, RemoveScriveElementsSpec.class);
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

        // remove verification pages
        final int n = reader.getNumberOfPages();
        String keep = "";
        for (int i = 1; i <= n; i++) {
            PdfDictionary page = reader.getPageN(i);
            if (spec.removeVerificationPages && AddVerificationPages.scriveTagVerPage.equals(page.get(AddVerificationPages.scriveTag)))
                continue; // remove the whole page
            keep = keep.isEmpty() ? String.valueOf(i) : keep + "," + i;
            
            PdfDictionary res = page.getAsDict(PdfName.RESOURCES);
            PdfDictionary xobj = (null == res) ? null : res.getAsDict(PdfName.XOBJECT);
            if (null != xobj)
                for (PdfName key: xobj.getKeys()) {
                    PdfStream x = (PdfStream)reader.getPdfObject(xobj.getAsIndirectObject(key).getNumber());
                    if ((null == x) || !PdfName.FORM.equals(x.get(PdfName.SUBTYPE)))
                        continue;
                }
            
            byte[] content = reader.getPageContent(i);
            byte[] content2 = checkContent(content, res);
            if (content2 != content)
                reader.setPageContent(i,  content2);
        }
        reader.selectPages(keep);

        stamper.close();
        reader.close();
        pdf.close();
    }

	private byte[] checkContent(byte[] content, PdfDictionary resources) throws IOException {
		// TODO: find & remove BMC..EMC sections with Scrvie* tags
		return content;
	}
}
