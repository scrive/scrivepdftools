import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Date;

import org.apache.commons.fileupload.MultipartStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class WebServer {

    public static void start(String specFile, String ip, int port)
            throws IOException
    {
        InetSocketAddress address = ((ip != null) && !ip.isEmpty()) ? new InetSocketAddress(ip, port) : new InetSocketAddress(port);  
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
        final String[] commands = {"add-verification-pages", "find-texts", "extract-texts", "normalize", "remove-scrive-elements", "select-and-clip"};
        for (String cmd: commands)
            server.createContext("/" + cmd, new ExecHandler(cmd));

        System.out.println("HTTP server starting on " + address.getHostName() + ":" + address.getPort());
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
            int code = 200;
            String response = "";
            System.out.println("\n->[" + (new Date()).toString() + "] Request " + t.getProtocol().toString() + "/" + t.getRequestMethod() + " from " + t.getRemoteAddress().toString());
            // load test HTML page
            try {
               BufferedReader reader = new BufferedReader(new FileReader(Main.getResource("assets/test-client.html"))); // use JAR resources if possible
               String line = reader.readLine();
               while (null != line) {
                   response += line + "\r\n";
                   line = reader.readLine();
               }
            } catch (Exception e) {
                e.printStackTrace(System.err);
                code = 500;
                response = "Error: Failed to load test page..";
            }
            // send response            
            t.sendResponseHeaders(code, response.length());
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
            } catch (IOException e) {
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
                    // Dispatch processing
                    String outFileName = pdfName + ".result.pdf", mime = "application/pdf";
                    if( command.equals("find-texts")) {
                        outFileName = pdfName + ".found-texts.yaml";
                        mime = "text/yaml";
                    } else if( command.equals("extract-texts")) {
                        outFileName = pdfName + "extracted-texts.yaml";
                        mime = "text/yaml";
                    }

                    byte[] out = (new Main()).execute(command, config, pdf);

                    t.getResponseHeaders().set("Content-Disposition", "attachment; filename=" + outFileName + "; size=" + out.length);
                    t.getResponseHeaders().set("Content-Type", mime);
                    t.sendResponseHeaders(code, out.length);
                    OutputStream os = t.getResponseBody();
                    os.write(out);
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