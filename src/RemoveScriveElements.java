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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

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

            // Remove background and footer (cut the content stream and delete resources)
            byte[] content = reader.getPageContent(i);
            byte[] content2 = checkContent(content, page.getAsDict(PdfName.RESOURCES));
            if (content2 != content)
                reader.setPageContent(i,  content2);
        }
        reader.selectPages(keep);

        // Remove embedded files
        PdfDictionary names = reader.getCatalog().getAsDict(PdfName.NAMES);
        if (null != names) {
            names.remove(PdfName.EMBEDDEDFILES);
            if (names.size() < 1)
                reader.getCatalog().remove(PdfName.NAMES);
        }
        reader.removeUnusedObjects();

        stamper.close();
        reader.close();
        pdf.close();
    }

    private interface IByteSearchListener {
        /**
         * @param position content stream position
         */

        /**
         * Callback for byte content stream linear search
         * @param tag      found tag
         * @param tagPos   tag position
         * @param id       preceding identifier
         * @param idPos    identifier position
         */
        public void onItem(byte[] tag, int tagPos, String id, int idPos);
    }

    private static class ResFinder implements IByteSearchListener {
        public static byte[] tag = "Do".getBytes();
        public Set<String>   refs = new HashSet<String>();
        public boolean       add = true;

        public void onItem(byte[] tag, int tagPos, String id, int idPos) {
            if (add) {
                refs.add(id);
            } else {
                refs.remove(id);
            }
        }
    }

    private static class BlockFinder implements IByteSearchListener {
        public static byte[]   tagBMC = "BMC".getBytes();
        public static byte[]   tagEMC = "EMC".getBytes();
        public static String[] toRemove = {"/Scrive:Background", "/Scrive:Footer"};

        public static class BlockIndex {
            public int start, end;
            public BlockIndex(int s, int e) {
                start = s;
                end = e;
            }
            public String toString() {
                return "{" + start + ":" + end + "}";
            }
        }

        private Stack<Integer> open = new Stack<Integer>();
        public ArrayList<BlockIndex> blocks = new ArrayList<BlockIndex>();

        public int getTotalLength() {
            int len = 0;
            for (BlockIndex i: blocks)
                len += i.end - i.start;
            return len;
        }

        private boolean check(String id) {
            for (String i: toRemove)
                if (i.equals(id))
                    return true;
            return false;
        }

        public void onItem(byte[] tag, int tagPos, String id, int idPos) {
            if (tagBMC == tag) {
                open.push(check(id) ? idPos : -idPos);
            } else if (tagEMC == tag) {
                final int start = open.peek().intValue();
                open.pop();
                if (start >= 0) {
                    /**
                     * Important: remove all blocks enclosed with the current one
                     */
                    while (!blocks.isEmpty() && (blocks.get(blocks.size() - 1).end > start))
                        blocks.remove(blocks.get(blocks.size() - 1));
                    blocks.add(new BlockIndex(start, tagPos + tag.length));
                }
            }
        }
    }

    /**
     *  Drives content search and calls listeners registered for specific tokens.
     * @param content
     * @param cb
     */
    private void search(byte[] content, Map<byte[], IByteSearchListener> cb) {
        boolean start = true;
        int ii = 0, k = 0, l = 0;
        for (int i = 0; i < content.length; ++i) {
            if (Character.isWhitespace(content[i])) {
                if (!start) {
                    k = ii; l = i - ii;
                    start = true;
                }
            } else if (start) {
                ii = i;
                start = false;
            }
            for (byte[] c: cb.keySet()) {
                if (i + c.length > content.length)
                    continue;
                int j = 0;
                while ((j < c.length) && (c[j] == content[i + j]))
                    ++j;
                if (j == c.length)
                    cb.get(c).onItem(c, i, new String(content, k, l), k); // report found item
            }
        }
    }

    private byte[] checkContent(byte[] content, PdfDictionary resources) throws IOException {
        // Track resource calls (forms and images)
        ResFinder res = new ResFinder();
        Map<byte[], IByteSearchListener> cb = new HashMap<byte[], IByteSearchListener>();
        cb.put(res.tag, res);
        // Find BMC..EMC blocks
        BlockFinder blocks = new BlockFinder();
        cb.put(blocks.tagBMC, blocks);
        cb.put(blocks.tagEMC, blocks);
        search(content, cb);

        // Remove BMC..EMC sections with Scrvie tags
        if (blocks.blocks.isEmpty())
            return content; // no changes required
        byte[] content2 = new byte[content.length - blocks.getTotalLength()];
        int j = 0, ii = 0;
        for (BlockFinder.BlockIndex i: blocks.blocks) {
            final int len = i.start - ii;
            System.arraycopy(content, ii, content2, j, len);
            j += len;
            ii = i.end;
        }
        System.arraycopy(content, ii, content2, j, content.length - ii);

        // Update resource refs
        cb.clear();
        cb.put("Do".getBytes(), res);
        res.add = false;
        search(content2, cb);

        // Remove resources not referenced any more in new content
        final PdfDictionary xobj = (null != resources) ? resources.getAsDict(PdfName.XOBJECT) : null;
        if (null != xobj) {
            for (String r: res.refs) {
                xobj.remove(new PdfName(r.substring(1)));
            }
        }
        return content2;
    }
}
