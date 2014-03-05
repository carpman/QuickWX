package org.voidptr.QuickWX;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.*;
import android.view.View;
import android.widget.TextView;
import org.apache.http.cookie.CookieAttributeHandler;

public class MainView extends Activity {
    Messenger mService = null;
    boolean mIsBound;
    Bundle currentConditions;

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg){
            switch (msg.what){
                case QuickWXService.MSG_REQUEST_CONDITIONS:
                    Bundle response = (Bundle)msg.obj;
                    if(response.containsKey("status")){
                        switch (response.getInt("status")){
                            case QuickWXService.RESULT_STATUS_UPDATING:
                                findViewById(R.id.loadingSpinner).setVisibility(View.VISIBLE);
                                break;
                            case QuickWXService.RESULT_STATUS_OK:
                                findViewById(R.id.loadingSpinner).setVisibility(View.GONE);
                                setCurrentConditions(response);
                                break;
                        }
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    final Messenger mMessenger = new Messenger(new IncomingHandler());

    private ServiceConnection mConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mService = new Messenger(iBinder);
            try{
                Message msg = Message.obtain(null, QuickWXService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
                msg = Message.obtain(null, QuickWXService.MSG_REQUEST_CONDITIONS);
                msg.replyTo = mMessenger;
                mService.send(msg);
            }catch(RemoteException e){
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
        }
    };

    void doBindService(){
        bindService(new Intent(MainView.this, QuickWXService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doRequestConditions(){
        if(mIsBound){
            if(mService != null){
                try{
                    Message msg = Message.obtain(null, QuickWXService.MSG_REQUEST_CONDITIONS);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e){
                    e.printStackTrace();
                }
            }
        }
    }

    void doUpdateConditions(){
        if(mIsBound){
            if(mService != null){
                try{
                    Message msg = Message.obtain(null, QuickWXService.MSG_UPDATE_CONDITIONS);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e){
                    e.printStackTrace();
                }
            }
        }
    }

    void doUnbindService(){
        if(mIsBound){
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    void setCurrentConditions(Bundle newConditions){
        this.currentConditions = newConditions;

        if(this.currentConditions.containsKey("temperature")){
            TextView temperatureView = (TextView)findViewById(R.id.temperatureTextView);
            temperatureView.setText(new Double(this.currentConditions.getDouble("temperature")).toString());
        }
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }

    @Override
    protected void onStart(){
        super.onStart();
        doBindService();
    }

    @Override
    protected void onStop(){
        super.onStop();
        doUnbindService();
    }

    @Override
    protected void onResume(){
        super.onResume();
        findViewById(R.id.loadingSpinner).setVisibility(View.VISIBLE);
    }
}
