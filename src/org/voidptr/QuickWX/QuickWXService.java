package org.voidptr.QuickWX;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by carpman on 3/5/14.
 */
public class QuickWXService extends Service {
    Bundle currentConditions = new Bundle();
    ArrayList<Messenger> clients = new ArrayList<Messenger>();
    boolean running = false;
    boolean updating = false;

    static final int MSG_REQUEST_CONDITIONS = 1;
    static final int MSG_UPDATE_CONDITIONS = 2;
    static final int MSG_REGISTER_CLIENT = 3;
    static final int MSG_UNREGISTER_CLIENT = 4;

    static final int RESULT_STATUS_OK = 1;
    static final int RESULT_STATUS_UPDATING = 2;
    static final int RESULT_STATUS_ERROR = 3;

    private String extractNodeValue(Document doc, String nodeName){
        NodeList nodeList = doc.getElementsByTagName(nodeName);
        Node target = nodeList.item(0);
        return target.getChildNodes().item(0).getNodeValue();
    }

    private void updateConditions(){
        try{
            URL conditionsURL = new URL("http://w1.weather.gov/xml/current_obs/KDAL.xml");
            URLConnection connection = conditionsURL.openConnection();
            InputStream in = new BufferedInputStream(connection.getInputStream());

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(in);

            currentConditions.putString("observation_made_at", extractNodeValue(doc, "observation_time_rfc822"));
            currentConditions.putDouble("temperature", new Double(extractNodeValue(doc, "temp_f")));
            currentConditions.putInt("relative_humidity", Integer.valueOf(extractNodeValue(doc, "relative_humidity")).intValue());
            currentConditions.putInt("status", RESULT_STATUS_OK);
        }catch (MalformedURLException e){
            e.printStackTrace();
        }catch (IOException e){
            //TODO: This might actually happen, do something about it
            currentConditions.putInt("status", RESULT_STATUS_ERROR);
            e.printStackTrace();
        }catch(ParserConfigurationException e){
            //A wha?
            e.printStackTrace();
        }catch(SAXException e){
            e.printStackTrace();
        }
    }

    private class RetrieveConditionsTask extends AsyncTask<String, Void, Integer>{

        @Override
        protected Integer doInBackground(String... strings) {
            updateConditions();
            for(Messenger m : clients){
                try{
                    m.send(Message.obtain(null, MSG_REQUEST_CONDITIONS, currentConditions));
                }catch (RemoteException e){
                    clients.remove(m);
                }
            }
            return new Integer(0);
        }
    };

    final Thread updateThread = new Thread(new Runnable() {
        @Override
        public void run() {
            while(running){
                try{
                    updating = true;
                    updateConditions();
                    updating = false;
                    for(Messenger m : clients){
                        try{
                            m.send(Message.obtain(null, MSG_REQUEST_CONDITIONS, currentConditions));
                        }catch (RemoteException e){
                            clients.remove(m);
                        }
                    }
                    //Todo: make refresh time configurable
                    Thread.sleep(900);
                }catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        }
    });

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg){
            switch(msg.what){
                case MSG_REGISTER_CLIENT:
                    clients.add(msg.replyTo);
                    break;
                case MSG_REQUEST_CONDITIONS:
                    try{
                        if(updating){
                            currentConditions.putInt("status", RESULT_STATUS_UPDATING);
                        }
                        msg.replyTo.send(Message.obtain(null, MSG_REQUEST_CONDITIONS, currentConditions));
                    }catch(RemoteException e){
                        e.printStackTrace();
                    }
                    break;
                case MSG_UPDATE_CONDITIONS:
                    new RetrieveConditionsTask().execute();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    final Messenger mMessenger = new Messenger(new IncomingHandler());

    public void onCreate(){
        super.onCreate();
        running = true;
        updateThread.start();
    }

    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }
}
