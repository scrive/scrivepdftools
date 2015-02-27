import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.Date;

import org.apache.commons.fileupload.MultipartStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class WebServer {

    public static void execute(String specFile, String port)
            throws IOException
    {
        int i = port.lastIndexOf(":", port.length());
        InetSocketAddress address = ( i < 0 ) ? new InetSocketAddress(Integer.valueOf(port)) : new InetSocketAddress(port.substring(0, i), Integer.valueOf(port.substring(i + 1)));
        System.out.println("HTTP server starting on " + address.getHostName() + ":" + address.getPort());
        HttpServer server = HttpServer.create(address, 0);
        server.setExecutor(null); // use default

        // Init test client contexts
        TestHandler main = new TestHandler(); 
        server.createContext("/", main);
        server.createContext("/main.html", main);
        server.createContext("/main.htm", main);
        server.createContext("/index.html", main);
        server.createContext("/index.htm", main);

        // Init PDF processing context for each command
        final String[] commands = {"add-verification-pages", "find-texts", "extract-texts", "normalize", "select-and-clip"};
        for (String cmd: commands)
            server.createContext("/" + cmd, new ExecHandler(cmd));

        server.start();
    }

    /**
     * This handler provides convenient web browser based client
     * that can be used to upload files and test HTTP communication 
     *
     */
    static class TestHandler implements HttpHandler
    {
        public void handle(HttpExchange t) throws IOException
        {
            String response = "";
            System.out.println("\n->[" + (new Date()).toString() + "] Request " + t.getProtocol().toString() + "/" + t.getRequestMethod() + " from " + t.getRemoteAddress().toString());
            try {
                // load HTML from resources
                BufferedReader reader = null;
                try {
                   reader = new BufferedReader(new InputStreamReader(WebServer.class.getResourceAsStream("src/test-client.html")));
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                    reader = new BufferedReader(new FileReader("src/test-client.html"));
                }
                String line = reader.readLine();
                while (null != line) {
                    response += line + "\r\n";
                    line = reader.readLine();
                }
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
            // send response            
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes("UTF-8"));
            os.close();
        }
    }
    static private String getVal(String line, String key)
    {
        final int i = line.indexOf(key);
        if (i < 0)
            return null;
        final int i1 = line.indexOf("\r\n", i);
        return (i1 < 0) ? line.substring(i + key.length()) : line.substring(i + key.length(), i1);
    }
    static class ExecHandler implements HttpHandler
    {
        String command;
        byte[] config = null;
        byte[] pdf = null;
        String configName = null;
        String pdfName = null;

        ExecHandler(String command)
        {
            this.command = command;
        }

        private void onFormField(String disp, String ctype, byte[] data)
        {
            if ((data.length == 0) || disp.isEmpty())
                return;
            disp = getVal(disp, "form-data; name=\"");
            String fname = getVal(disp, "filename=\"");
            if ((fname != null) && (0 < fname.indexOf('"'))) {
                fname = fname.substring(0, fname.indexOf('"'));
            }
            if (disp.startsWith("config")) {
                config = data;
                configName = fname;                    
            } else if (disp.startsWith("pdf")) {
                pdf = data;
                pdfName = fname;
            }
        }
        
        /**
         * This method extracts useful data from multipart POST form fields
         * @param disp  Content-Disposition
         * @param ctype Content-Type
         * @param data  field content
         */
        private void onFormField(String disp, String ctype, String data) throws UnsupportedEncodingException
        {
            if (data.isEmpty() || disp.isEmpty())
                return;
            disp = getVal(disp, "form-data; name=\"");
            String fname = getVal(disp, "filename=\"");
            if ((fname != null) && (0 < fname.indexOf('"'))) {
                fname = fname.substring(0, fname.indexOf('"'));
            }
            if (disp.startsWith("config")) {
                config = data.getBytes("UTF-8");
                configName = fname;                    
            } else if (disp.startsWith("pdf")) {
                pdf = data.getBytes("UTF-8");
                pdfName = fname;
            }
        }

        /**
         * byte version of String.indexOf(...)
         */        
        static private int indexOf(byte[] data, byte[] seq, int start)
        {
            final int n = data.length, m = seq.length;
            for (int i = start; i + m < n; ++i) {
                boolean found = true;
                for (int j = 0; j < m; ++j)
                    if (data[i + j] != seq[j]) {
                        found = false;
                        break;
                    }
                if (found)
                    return i;
            }
            return -1;
        }
        
        /**
         * This method parses multipart form encapsulated in HTTP 1.1 POST request
         */
        private boolean parseRequest(InputStream body, String boundary) throws IOException
        {
            try {
                MultipartStream multipartStream = new MultipartStream(body, boundary.getBytes(), 16384, null); // 16 kB buffer 
                boolean nextPart = multipartStream.skipPreamble();
                while (nextPart) {
                    String header = multipartStream.readHeaders();
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();  
                    multipartStream.readBodyData(buf);
                    onFormField(getVal(header, "Content-Disposition: "), getVal(header, "Content-Type: "), buf.toByteArray());
                    nextPart = multipartStream.readBoundary();
                }            
            } catch (Exception e) {
                e.printStackTrace(System.err);
                throw e;
            }
            if ((config == null) || (pdf == null) || (pdfName == null) || (configName == null))
                return false;
            System.out.println("Uploaded \"" + pdfName + "\" (" + pdf.length + " bytes) and \"" + configName + "\" for: " + command);
            return true;
        }
        
        public void handle(HttpExchange t) throws IOException
        {
            String response = "200 OK";
            int code = 200;
            
            // Parse the request
            try { 
                System.out.println("\n->[" + (new Date()).toString() + "] Request " + t.getProtocol().toString() + "/" + t.getRequestMethod() + " from " + t.getRemoteAddress().toString());
                final String mpart = "multipart/form-data; boundary=";
                String ctype = t.getRequestHeaders().getFirst("Content-type");
                if ((null == ctype) || !ctype.startsWith(mpart)) {
                    response = "Error 400: Content-type not recognized: " + ctype; 
                    code = 400;
                } else if (!parseRequest(t.getRequestBody(), getVal(ctype, mpart))) {
                    code = 400;
                    response = "Error 400: Failed to parse request body"; 
                } else {
                    // TODO: skip temp files and connect I/O directly to processors
                    final String path = "tmp/";
                    FileOutputStream tmp = new FileOutputStream(path + configName);
                    tmp.write(config);
                    tmp.close();
                    tmp = new FileOutputStream(path + pdfName);
                    tmp.write(pdf);
                    tmp.close();
                    
                    // Dispatch processing
                    String outFileName = pdfName + ".result.pdf", mime = "application/pdf";
                    boolean yaml = false;
                    if( command.equals("find-texts")) {
                        outFileName = pdfName + ".found-texts.yaml";
                        yaml = true;
                    } else if( command.equals("extract-texts")) {
                        outFileName = pdfName + "extracted-texts.yaml";
                        yaml = true;
                    }
                    ByteArrayOutputStream cap = new ByteArrayOutputStream();
                    PrintStream original = System.out;
                    System.setOut(new PrintStream(cap));
                    (new Main()).execute(command, path + configName, path + pdfName, yaml ? null : (path + outFileName));
                    System.setOut(original);

                    // TODO: skip temp files and connect I/O directly to processors
                    long size = cap.size();
                    File oo = new File(path + outFileName);
                    if (yaml)
                        mime = "text/yaml";
                    else
                        size = oo.length();
                    t.getResponseHeaders().set("Content-Disposition", "attachment; filename=" + outFileName + "; size=" + size);
                    t.getResponseHeaders().set("Content-Type", mime);
                    t.sendResponseHeaders(code, size);
                       OutputStream os = t.getResponseBody();
                    if (yaml) {
                        os.write(cap.toByteArray());
                    } else {
                        byte[] buf = new byte[32 * 1024];
                        FileInputStream tmp2 = new FileInputStream(oo);
                        for (;;) {
                            int len = tmp2.read(buf);
                            if (len < 0)
                                break;
                            os.write(buf, 0, len);
                        }
                        tmp2.close();
                    }
                    os.close();
                }
            } catch (Exception e) {
                e.printStackTrace(System.err);
                code = 400;
                response = "Error 400: Failed to process the request";
            }

            // Send response
            t.sendResponseHeaders(code, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes("UTF-8"));
            os.close();
            if (code == 200)
                System.out.println('\t' + response);
            else
                System.err.println('\t' + response);
        }
    }
}