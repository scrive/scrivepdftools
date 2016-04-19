import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class HistEntry {
    public String date;
    public String comment;
    public String address;

    static public HistEntry FromJSON(JSONObject obj) throws JSONException {
        HistEntry histEntry = new HistEntry();
        histEntry.date = obj.getString("date");
        histEntry.comment = obj.getString("comment");
        histEntry.address = obj.getString("address");

        return histEntry;
    }
}

class Person {
    public String fullname;
    public String company;
    public String personalnumber;
    public String companynumber;
    public String email;
    public String phone;
    public Boolean fullnameverified;
    public Boolean companyverified;
    public Boolean numberverified;
    public Boolean emailverified;
    public Boolean phoneverified;
    public ArrayList<Field> fields;
    public String signtime;
    public String signedAtText;
    public String personalNumberText;
    public String companyNumberText;

    static public Person FromJSON(JSONObject obj) throws JSONException {
        Person p = new Person();

        p.fullname = obj.getString("fullname");
        p.company = obj.getString("company");
        p.personalnumber = obj.getString("personalnumber");
        p.companynumber = obj.getString("companynumber");
        p.email = obj.getString("email");
        p.phone = obj.getString("phone");
        p.fullnameverified = obj.getBoolean("fullnameverified");
        p.companyverified = obj.getBoolean("companyverified");
        p.numberverified = obj.getBoolean("numberverified");
        p.emailverified = obj.getBoolean("emailverified");
        p.phoneverified = obj.getBoolean("phoneverified");
        p.signtime = obj.getString("signtime");
        p.signedAtText = obj.getString("signedAtText");
        p.personalNumberText = obj.getString("personalNumberText");
        p.companyNumberText = obj.getString("companyNumberText");

        JSONArray arr = obj.getJSONArray("fields");
        p.fields = new ArrayList<Field>();
        for (int i = 0; i < arr.length(); i++) {
            p.fields.add(Field.FromJSON(arr.getJSONObject(i)));
        }

        return p;
    }
}

class Field {
    public String valueBase64;
    public String value;
    public float x, y;
    public int page;
    public float image_w;
    public float image_h;
    public Boolean includeInSummary;
    public boolean onlyForSummary;
    public float fontSize;
    public Boolean greyed;
    public ArrayList<Integer> keyColor;

    static public Field FromJSON(JSONObject obj) throws JSONException {
        Field field = new Field();
        field.valueBase64 = obj.optString("valueBase64", null);
        field.value = obj.optString("value", null);
        field.x = (float)obj.getDouble("x");
        field.y = (float)obj.getDouble("y");
        field.page = obj.getInt("page");
        field.image_w = (float)obj.optDouble("image_w");
        field.image_h = (float)obj.optDouble("image_h");
        field.includeInSummary = obj.getBoolean("includeInSummary");
        field.onlyForSummary = obj.optBoolean("onlyForSummary", false);
        field.fontSize = (float)obj.optDouble("fontSize");
        try {
            field.greyed = obj.getBoolean("greyed");
        } catch (JSONException e) {
            field.greyed = null;
        }
        JSONArray keyColorArr = obj.optJSONArray("keyColor");
        if (keyColorArr != null) {
            field.keyColor = new ArrayList<Integer>(3);
            field.keyColor.add(keyColorArr.getInt(0));
            field.keyColor.add(keyColorArr.getInt(1));
            field.keyColor.add(keyColorArr.getInt(2));
        }
        return field;
    }
}

class SealingTexts {
    public String verificationTitle;
    public String partnerText;
    public String initiatorText;
    public String documentText;
    public String eventsText;
    public String dateText;
    public String historyText;
    public String verificationFooter;
    public String hiddenAttachmentText;
    public String onePageText;

    static public SealingTexts FromJSON(JSONObject obj) throws JSONException {
        SealingTexts sealingTexts = new SealingTexts();

        sealingTexts.verificationTitle = obj.getString("verificationTitle");
        sealingTexts.partnerText = obj.getString("partnerText");
        sealingTexts.initiatorText = obj.getString("initiatorText");
        sealingTexts.documentText = obj.getString("documentText");
        sealingTexts.eventsText = obj.getString("eventsText");
        sealingTexts.dateText = obj.getString("dateText");
        sealingTexts.historyText = obj.getString("historyText");
        sealingTexts.verificationFooter = obj.getString("verificationFooter");
        sealingTexts.hiddenAttachmentText = obj.getString("hiddenAttachmentText");
        sealingTexts.onePageText = obj.getString("onePageText");

        return sealingTexts;
    }
}

class SealAttachment {
    public String fileName;
    public String mimeType;
    public String fileBase64Content;

    static public SealAttachment FromJSON(JSONObject obj) throws JSONException {
        SealAttachment sealAttachment = new SealAttachment();
        sealAttachment.fileBase64Content = obj.getString("fileBase64Content");
        sealAttachment.fileName = obj.getString("fileName");
        sealAttachment.mimeType = obj.optString("mimeType", null);

        return sealAttachment;
    }
}

class FileDesc {
    public String title;
    public String role;
    public String pagesText;
    public String attachedBy;
    public String attachedToSealedFileText;
    public String input;

    static public FileDesc FromJSON(JSONObject obj) throws JSONException {
        FileDesc fileDesc = new FileDesc();
        fileDesc.title = obj.getString("title");
        fileDesc.role = obj.getString("role");
        fileDesc.pagesText = obj.getString("pagesText");
        fileDesc.attachedBy = obj.getString("attachedBy");
        fileDesc.attachedToSealedFileText = obj.optString("attachedToSealedFileText", null);
        fileDesc.input = obj.optString("input", null);
        return fileDesc;
    }
}

class SealSpec {
    public boolean preseal;
    public String input = null;
    public String output = null;
    public String documentNumberText;
    public ArrayList<Person> persons;
    public ArrayList<Person> secretaries;
    public Person initiator;
    public ArrayList<HistEntry> history;
    public String initialsText;
    // public String hostpart;
    public SealingTexts staticTexts;
    public ArrayList<SealAttachment> attachments;
    public ArrayList<FileDesc> filesList;
    public ArrayList<Field> fields;
    public ArrayList<String> fonts;
    public String background;

    static public SealSpec FromJSON(InputStream specFile) throws IOException, JSONException {
        StringWriter writer = new StringWriter();
        IOUtils.copy(specFile, writer, "UTF-8");
        JSONObject obj = new JSONObject(writer.toString());
        return SealSpec.FromJSON(obj);
    }

    static public SealSpec FromJSON(JSONObject obj) throws JSONException {
        SealSpec spec = new SealSpec();

        spec.preseal = obj.optBoolean("preseal", false);
        spec.input = obj.getString("input");
        spec.output = obj.getString("output");
        spec.documentNumberText = obj.optString("documentNumberText");
        spec.initialsText = obj.optString("initialsText");
        // spec.hostpart = obj.getString("hostpart");
        spec.background = obj.optString("background", null);

        JSONArray arr = obj.optJSONArray("filesList");
        spec.filesList = new ArrayList<FileDesc>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                spec.filesList.add(FileDesc.FromJSON(arr.getJSONObject(i)));
            }
        }

        arr = obj.optJSONArray("fields");
        spec.fields = new ArrayList<Field>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                spec.fields.add(Field.FromJSON(arr.getJSONObject(i)));
            }
        }

        arr = obj.optJSONArray("persons");
        spec.persons = new ArrayList<Person>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                spec.persons.add(Person.FromJSON(arr.getJSONObject(i)));
            }
        }

        arr = obj.optJSONArray("secretaries");
        spec.secretaries = new ArrayList<Person>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                spec.secretaries.add(Person.FromJSON(arr.getJSONObject(i)));
            }
        }

        arr = obj.optJSONArray("history");
        spec.history = new ArrayList<HistEntry>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                spec.history.add(HistEntry.FromJSON(arr.getJSONObject(i)));
            }
        }

        arr = obj.optJSONArray("attachments");
        spec.attachments = new ArrayList<SealAttachment>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                spec.attachments.add(SealAttachment.FromJSON(arr.getJSONObject(i)));
            }
        }

        JSONObject staticTextsJson = obj.optJSONObject("staticTexts");
        if (staticTextsJson != null) {
            spec.staticTexts = SealingTexts.FromJSON(staticTextsJson);
        } else {
            spec.staticTexts = null;
        }
        JSONObject initiator = obj.optJSONObject("initiator");
        if (initiator != null) {
            spec.initiator = Person.FromJSON(initiator);
        }

        try {
            arr = obj.getJSONArray("fonts");
            spec.fonts = new ArrayList<String>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                spec.fonts.add(arr.getString(i));
            }
        } catch (JSONException e) {
            spec.fonts = null;
        }

        return spec;
    }
}
