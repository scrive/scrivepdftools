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
import java.util.HashSet;
import java.util.Set;

import com.itextpdf.text.DocWriter;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfArray;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfLiteral;
import com.itextpdf.text.pdf.PdfName;
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

public class Normalize extends Engine {

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

    /**
     * Detects specific scenario in which every even page of a document is
     * empty. In this case all even pages are removed.
     * 
     * @param reader
     *            - input PDF
     */
    private static void removeEveryEvenPageEmpty(PdfReader reader) throws IOException {
        // this class checks whether a selected page is empty (no drawing
        // operators in PDF content)
        class DetectEmptyPage implements ContentOperator, RenderListener {
            private ContentOperator original = null;
            private boolean empty = true;
            private Set<String> ops; // set of *drawing* operators to be found
                                     // in PDF content

            DetectEmptyPage() {
                ops = new HashSet<String>();
                final String [] oplist = {"f", "F", "S", "s", "f*", "B", "B*", "b", "b*", "Tj", "TJ", "'", "\"", "Do", "BI", "sh"};
                for (String o: oplist)
                    ops.add(o);
            }

            public void invoke(PdfContentStreamProcessor processor, PdfLiteral operator, ArrayList<PdfObject> operands)
                    throws Exception {
                original.invoke(processor, operator, operands);
                if (ops.contains(operator.toString()))
                    empty = false;
            }

            public void beginTextBlock() {
            }

            public void renderText(TextRenderInfo renderInfo) {
                empty = false;
            }

            public void endTextBlock() {
            }

            public void renderImage(ImageRenderInfo renderInfo) {
                empty = false;
            }

            public boolean isPageEmpty(PdfReader reader, int iPage) throws IOException {
                empty = true;
                PdfContentStreamProcessor processor = new PdfContentStreamProcessor(this);
                original = processor.registerContentOperator(PdfContentStreamProcessor.DEFAULTOPERATOR, this);
                processor.processContent(ContentByteUtils.getContentBytesForPage(reader, iPage), reader.getPageN(iPage)
                        .getAsDict(PdfName.RESOURCES));
                return empty;
            }
        }
        // check if ALL even pages are empty
        DetectEmptyPage detect = new DetectEmptyPage();
        final int n = reader.getNumberOfPages();
        String keep = "";
        for (int i = 1; i <= n; i++) {
            if (!detect.isPageEmpty(reader, i))
                keep = keep.isEmpty() ? String.valueOf(i) : keep + "," + i; // keep
                                                                            // non-empty
                                                                            // pages
        }
        // remove all even pages
        reader.selectPages(keep);
    }

    /**
     * This method detects text direction and 'Rotate' flag on each page and
     * applies auto-rotation to output horizontal text. 'Rotate' flag is
     * removed.
     * 
     * @param reader
     *            - input PDF
     * @param stamper
     *            - output writer
     */
    private static void unrotatePages(PdfReader reader, PdfStamper stamper) throws IOException {
        final int n = reader.getNumberOfPages();
        for (int i = 1; i <= n; i++) {
            PdfDictionary page = reader.getPageN(i);
            final int rot = -(new PageText(reader, i, null)).detectRotate();
            page.put(PdfName.ROTATE, null);
            if (rot == 0)
                continue; // no need to rotate
            PdfObject content = PdfReader.getPdfObject(page.get(PdfName.CONTENTS), page);
            PdfArray ar = null;
            if (content == null)
                continue;
            else if (content.isStream()) {
                ar = new PdfArray();
                ar.add(page.get(PdfName.CONTENTS));
                page.put(PdfName.CONTENTS, ar);
            } else if (content.isArray()) {
                ar = (PdfArray) content;
            }
            String ctm0 = null;
            PdfArray media = page.getAsArray(PdfName.MEDIABOX);
            Rectangle bbox1 = null;
            final float m[] = { media.getAsNumber(0).floatValue(), media.getAsNumber(1).floatValue(),
                    media.getAsNumber(2).floatValue(), media.getAsNumber(3).floatValue() };
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
            case 270:
                ctm0 = "0 1 -1 0 " + (y0 + h0) + " " + (-x0) + " cm\n";
                bbox1 = new Rectangle(0, 0, h0, w0);
                break;
            case 0:
            default:
                continue;
            }
            if (bbox1 != null) {
                float bb1[] = { bbox1.getLeft(), bbox1.getBottom(), bbox1.getRight(), bbox1.getTop() };
                page.put(PdfName.MEDIABOX, new PdfArray(bb1));
                page.put(PdfName.CROPBOX, null); // TODO: calculate CORRECT
                                                 // cropbox !
            }
            PdfStream stream2 = new PdfStream(DocWriter.getISOBytes(ctm0));
            ar.addFirst(stamper.getWriter().addToBody(stream2).getIndirectReference());
            stream2.flateCompress(PdfStream.DEFAULT_COMPRESSION);
        }
    }

    public void execute(InputStream pdf, OutputStream os) throws IOException, DocumentException {
        if (pdf == null)
            pdf = new FileInputStream(spec.input);
        if (os == null)
            os = new FileOutputStream(spec.output);
        PdfReader reader = new PdfReader(pdf);
        PdfStamper stamper = new PdfStamper(reader, os);

        stamper.setFormFlattening(true);
        stamper.setFreeTextFlattening(true);
        removeEveryEvenPageEmpty(reader);
        unrotatePages(reader, stamper);

        stamper.close();
        reader.close();
        pdf.close();
    }
}
