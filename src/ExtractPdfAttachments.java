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
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
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
import com.itextpdf.text.pdf.PdfEFStream;
import com.itextpdf.text.pdf.PRStream;
import com.itextpdf.text.pdf.PdfIndirectReference;
import com.itextpdf.text.pdf.parser.ContentByteUtils;
import com.itextpdf.text.pdf.parser.ContentOperator;
import com.itextpdf.text.pdf.parser.ImageRenderInfo;
import com.itextpdf.text.pdf.parser.PdfContentStreamProcessor;
import com.itextpdf.text.pdf.parser.RenderListener;
import com.itextpdf.text.pdf.parser.TextRenderInfo;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.nodes.Tag;

class PdfAttachment implements Serializable {

    public String name;
    private PRStream content;

    public PRStream getContent() {
      return this.content;
    }

    public PdfAttachment(String name, PRStream content) {
      this.name = name;
      this.content = content;

    }
}

class AttachmentsListRes extends YamlSpec
{
    public ArrayList<PdfAttachment> attachments = null;
    static private ArrayList<TypeDescription> td = null;

}

class ExtractPdfAttachmentsSpec extends YamlSpec
{
    public boolean list = false;
    public String fetchAttachmentWithName = null;
}

public class ExtractPdfAttachments extends Engine
{
    ExtractPdfAttachmentsSpec spec = null;

    public void Init(InputStream specFile, String inputOverride, String outputOverride) throws IOException {
        spec = ExtractPdfAttachmentsSpec.loadFromStream(specFile, ExtractPdfAttachmentsSpec.class);
        if (inputOverride != null) {
            spec.input = inputOverride;
        }
        if (outputOverride != null) {
            spec.output = outputOverride;
        }
    }

    public static ArrayList<PdfAttachment> extractAttachments(PdfReader reader) throws IOException
    {
        ArrayList <PdfAttachment> res = new ArrayList();
        int numObjs = reader.getXrefSize();
        for (int i = 0; i < numObjs; i++) {
            final PdfObject obj = reader.getPdfObject(i);
            if (obj instanceof PdfDictionary) {
                PdfDictionary desc = (PdfDictionary) obj;
                if (desc.checkType(new PdfName("Filespec"))) {
                    String name  = desc.get(new PdfName("F")).toString();
                    PdfDictionary ef = desc.getAsDict(new PdfName("EF"));
                    if (name != null && ef != null) {
                        PdfIndirectReference ref = ef.getAsIndirectObject(new PdfName("F"));
                        if (ref != null && reader.getPdfObject(ref) != null && reader.getPdfObject(ref) instanceof PRStream) {
                          PRStream stream = (PRStream) reader.getPdfObject(ref);
                          res.add(new PdfAttachment(name, stream));
                        }
                    }

                }
            }
        }
        return res;
    }

    public void printAttachmentListsJson(ArrayList<PdfAttachment> attachments, OutputStream out) throws IOException
    {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN );
        options.setPrettyFlow(true);
        options.setWidth(Integer.MAX_VALUE);
        Representer representer = new MyRepresenter();
        representer.addClassTag(AttachmentsListRes.class,Tag.MAP);
        Yaml yaml = new Yaml(representer, options);
        AttachmentsListRes spec = new AttachmentsListRes();
        spec.attachments = attachments;
        String json = yaml.dump(spec);
        PrintStream ps = new PrintStream((out == null) ? System.out : out, true, "utf-8");
        ps.println(json);
    }

    public void printAttachmentContent(String name, ArrayList<PdfAttachment> attachments, PdfReader reader, OutputStream out) throws IOException {
        for (int i = 0; i < attachments.size(); i++) {
            if (attachments.get(i).name.equals(name)) {
                PrintStream ps = new PrintStream((out == null) ? System.out : out, true, "utf-8");
                ps.print(new String(reader.getStreamBytes(attachments.get(i).getContent())));
                return;
            }
        }

        System.out.println("Attachment not found in PDF");
        throw new IOException("Attachment not found in PDF");
    }

    public void execute(InputStream pdf, OutputStream out) throws IOException, DocumentException
    {
        if (pdf == null) {
            pdf = new FileInputStream(spec.input);
        }
        if (out == null && spec.output != null) {
            out = new FileOutputStream(spec.output);
        }

        PdfReader reader = new PdfReader(pdf);
        ArrayList<PdfAttachment> attachments = this.extractAttachments(reader);

        if (spec.list) {
          printAttachmentListsJson(attachments,out);
        } else if (spec.fetchAttachmentWithName != null) {
          printAttachmentContent(spec.fetchAttachmentWithName, attachments, reader, out);
        } else {
          System.out.println("Invalid parameter for getting processing PDF's attachments");
          throw new IOException("Invalid parameter for getting processing PDF's attachments");
        }

        reader.close();
        pdf.close();
    }
}
