package com.example.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Date;

import com.google.firebase.database.DataSnapshot; // firebase 관련 import
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.logging.AndroidLogger;


public class MainActivity extends AppCompatActivity {
    TextView mTvBluetoothStatus;
    TextView mTvReceiveData;
    TextView mTvReceiveData2;
    TextView mTvReceiveData3;
    TextView mTvReceiveData4;
    TextView mTvReceiveData5;
    TextView mTvSendData;
    TextView mTvPercent;
    Button mBtnBluetoothOn;
    Button mBtnBluetoothOff;
    Button mBtnConnect;
    Button mBtnSendData;
    Button mBtnTransData;
    ImageView mImgSeat1;
    ImageView mImgSeat2;
    ImageView mImgSeat3;
    ImageView mImgSeat4;

    BluetoothAdapter mBluetoothAdapter;
    Set<BluetoothDevice> mPairedDevices;
    List<String> mListPairedDevices;

    Handler mBluetoothHandler;
    ConnectedBluetoothThread mThreadConnectedBluetooth;
    BluetoothDevice mBluetoothDevice;
    BluetoothSocket mBluetoothSocket;

    final static int BT_REQUEST_ENABLE = 1;
    final static int BT_MESSAGE_READ = 2;
    final static int BT_CONNECTING_STATUS = 3;
    final static UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    DatabaseReference mRootRef = FirebaseDatabase.getInstance().getReference(); // DB 테이블 불러오기
    DatabaseReference seatRef = mRootRef.child("좌석 상태"); // 컬럼(속성)명 선정
    DatabaseReference claimRef = mRootRef.child("문의 내역"); // 컬럼(속성)명 선정

    int percent;

    @IgnoreExtraProperties
    public class Info { // 시간 및 전화번호

        public String date;
        public String phoneNum;

        public Info(String date){ // 시간만 생성 (Seet 클래스에서 활용)
            date = getDate();
            phoneNum = null;
        }

        public Info(String date, String num) { // 시간과 전화번호 생성 (Claim 클래스에서 활용)
            this.date = getDate();
            phoneNum = getphoneNum();
        }

        public String getDate(){ // 날짜 불러오는 로직
            // 현재시간을 msec 으로 구한다.
            long now = System.currentTimeMillis();
            // 현재시간을 date 변수에 저장한다.
            Date dates = new Date(now);
            // 시간을 나타냇 포맷을 정한다 ( yyyy/MM/dd 같은 형태로 변형 가능 )
            SimpleDateFormat sdfNow = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            // nowDate 변수에 값을 저장한다.
            String formatDate = sdfNow.format(dates);

            return formatDate;
        }

        public String getphoneNum(){ // 전화번호 불러오는 로직
            String phoneNum = null;
            TelephonyManager telManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            try {
                phoneNum = telManager.getLine1Number();
                if(phoneNum.startsWith("+82")){
                    phoneNum = phoneNum.replace("+82", "0");
                }
            }catch (SecurityException e){
                e.printStackTrace();
            }
            return phoneNum;
        }
    }

    @IgnoreExtraProperties
    public class Seat { // 혼잡도 및 좌석 정보 클래스

        public int st_congestion; // 혼잡도
        public int st_emptySeat; // 좌석 상황
        public String date;

        public Seat() {
            // Default constructor required for calls to DataSnapshot.getValue(User.class)
        }

        public Seat(int congestion, int seetStat) {
            this.st_congestion = congestion;
            this.st_emptySeat = seetStat;
            this.date = getDate();
        }

        public String getDate(){ // 날짜 불러오는 로직
            // 현재시간을 msec 으로 구한다.
            long now = System.currentTimeMillis();
            // 현재시간을 date 변수에 저장한다.
            Date dates = new Date(now);
            // 시간을 나타냇 포맷을 정한다 ( yyyy/MM/dd 같은 형태로 변형 가능 )
            SimpleDateFormat sdfNow = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            // nowDate 변수에 값을 저장한다.
            String formatDate = sdfNow.format(dates);

            return formatDate;
        }

    }

    @IgnoreExtraProperties
    public class Claim { // 문의사항 클래스
        public String clam_1title;
        public String clam_2contents;
        public String clam_3station;
        public Info user_info;

        public Claim() {}

        public Claim(String title, String contents){
            this.clam_1title=title;
            this.clam_2contents=contents;
            this.clam_3station="역이름";
            user_info = new Info("claim","claim");
        }

    }


    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTvBluetoothStatus = (TextView)findViewById(R.id.tvBluetoothStatus);
        mTvReceiveData = (TextView)findViewById(R.id.tvReceiveData);
        mTvReceiveData2 = (TextView)findViewById(R.id.tvReceiveData2);
        mTvReceiveData3 = (TextView)findViewById(R.id.tvReceiveData3);
        mTvReceiveData4 = (TextView)findViewById(R.id.tvReceiveData4);
        mTvReceiveData5 = (TextView)findViewById(R.id.tvReceiveData5);
        mTvPercent = (TextView)findViewById(R.id.tvCongestion);
        mTvSendData =  (EditText) findViewById(R.id.tvSendData);
        mBtnBluetoothOn = (Button)findViewById(R.id.btnBluetoothOn);
        mBtnBluetoothOff = (Button)findViewById(R.id.btnBluetoothOff);
        mBtnConnect = (Button)findViewById(R.id.btnConnect);
        mBtnSendData = (Button)findViewById(R.id.btnSendData);
        mBtnTransData = (Button)findViewById(R.id.btnTransData);
        mImgSeat1 = (ImageView)findViewById(R.id.imgSeat1);
        mImgSeat2 = (ImageView)findViewById(R.id.imgSeat2);
        mImgSeat3 = (ImageView)findViewById(R.id.imgSeat3);
        mImgSeat4 = (ImageView)findViewById(R.id.imgSeat4);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        checkPermission();
        mBtnBluetoothOn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                bluetoothOn();
            }
        });
        mBtnBluetoothOff.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                bluetoothOff();
            }
        });
        mBtnConnect.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                listPairedDevices();
            }
        });
        mBtnSendData.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mThreadConnectedBluetooth != null) {
                    mThreadConnectedBluetooth.write(mTvSendData.getText().toString());
                    mTvSendData.setText("");
                }
            }
        });
        mBtnTransData.setOnClickListener(new View.OnClickListener() { // 전송하기 버튼을 누를 때
            @Override
            public void onClick(View v) {
                EditText editTitle = new EditText(MainActivity.this);
                    editTitle.setHint("제목을 입력하세요.");
                EditText editComment = new EditText(MainActivity.this);
                    editComment.setHint("내용을 입력하세요.");
                    editComment.setLines(5);
                LinearLayout layout = new LinearLayout(MainActivity.this);
                    layout.setOrientation(LinearLayout.VERTICAL);
                layout.addView(editTitle);
                layout.addView(editComment);
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);


                builder.setTitle("민원 신고");
                builder.setView(layout);
                builder.setPositiveButton("전송",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                Claim userClaim = new Claim(editTitle.getText().toString(), editComment.getText().toString()); // 문의 내역 받기
                                claimRef.push().setValue(userClaim); // 문의 내역 전송
                                Toast.makeText(getApplicationContext(),"전송하였습니다." ,Toast.LENGTH_LONG).show();
                            }
                        });
                builder.setNegativeButton("취소",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                Toast.makeText(getApplicationContext(),"취소하였습니다." ,Toast.LENGTH_LONG).show();
                            }
                        });
                builder.show();
            }
        }); // 파이어베이스 전송 모듈
        mBluetoothHandler = new Handler(){
            public void handleMessage(android.os.Message msg){
                if(msg.what == BT_MESSAGE_READ) {
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8"); // 수신된 데이터를 string으로 변환

                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }

                    // 수신된 데이터를 바이트(char) 단위로 분해하여 각각의 View 로 적재시킨다.
                    percent = ((readMessage.charAt(0) - '0') * 100) + ((readMessage.charAt(1) - '0') * 10) + (readMessage.charAt(2) - '0');

                    // 혼잡도 색상 설정
                    if (percent >= 50) {
                        if (percent >= 100)
                            mTvPercent.setTextColor(Color.RED);
                        else
                            mTvPercent.setTextColor(Color.YELLOW);
                    } else {
                        mTvPercent.setTextColor(Color.GREEN);
                    }

                    mTvPercent.setText(Integer.toString(percent) + '%');
                    mImgSeat1.setColorFilter(Color.parseColor((readMessage.charAt(3) - '0' > 0) ? "#990000" : "#009900"));
                    mImgSeat2.setColorFilter(Color.parseColor((readMessage.charAt(4) - '0' > 0) ? "#990000" : "#009900"));
                    mImgSeat3.setColorFilter(Color.parseColor((readMessage.charAt(5) - '0' > 0) ? "#990000" : "#009900"));
                    mImgSeat4.setColorFilter(Color.parseColor((readMessage.charAt(6) - '0' > 0) ? "#990000" : "#009900"));

                    Seat userSeat = new Seat(percent, 4-((readMessage.charAt(3) - '0')+(readMessage.charAt(4) - '0')+(readMessage.charAt(5) - '0')+(readMessage.charAt(6) - '0'))); // 빈 좌석 계산
                    seatRef.setValue(userSeat); // 좌석 정보 전송

                }
            }
        };
    }


    /* 아래는 블루투스 구현 코드 */
    public void checkPermission(){ // 전화번호 권한 요청 모듈
        //권한 허용 여부를 확인한다.
        int chk = checkCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);
        String [] req = {Manifest.permission.READ_PHONE_STATE};

        if(chk == PackageManager.PERMISSION_DENIED){
            //권한 허용을여부를 확인하는 창을 띄운다
            requestPermissions(req,0);
        }
    }
    void bluetoothOn() {
        if(mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "블루투스를 지원하지 않는 기기입니다.", Toast.LENGTH_LONG).show();
        }
        else {
            if (mBluetoothAdapter.isEnabled()) {
                Toast.makeText(getApplicationContext(), "블루투스가 이미 활성화 되어 있습니다.", Toast.LENGTH_LONG).show();
                mTvBluetoothStatus.setText("활성화");
            }
            else {
                Toast.makeText(getApplicationContext(), "블루투스가 활성화 되어 있지 않습니다.", Toast.LENGTH_LONG).show();
                Intent intentBluetoothEnable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intentBluetoothEnable, BT_REQUEST_ENABLE);
            }
        }
    }
    void bluetoothOff() {
        if (mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable();
            Toast.makeText(getApplicationContext(), "블루투스가 비활성화 되었습니다.", Toast.LENGTH_SHORT).show();
            mTvBluetoothStatus.setText("비활성화");
        }
        else {
            Toast.makeText(getApplicationContext(), "블루투스가 이미 비활성화 되어 있습니다.", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case BT_REQUEST_ENABLE:
                if (resultCode == RESULT_OK) { // 블루투스 활성화를 확인을 클릭하였다면
                    Toast.makeText(getApplicationContext(), "블루투스 활성화", Toast.LENGTH_LONG).show();
                    mTvBluetoothStatus.setText("활성화");
                } else if (resultCode == RESULT_CANCELED) { // 블루투스 활성화를 취소를 클릭하였다면
                    Toast.makeText(getApplicationContext(), "취소", Toast.LENGTH_LONG).show();
                    mTvBluetoothStatus.setText("비활성화");
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    void listPairedDevices() {
        if (mBluetoothAdapter.isEnabled()) {
            mPairedDevices = mBluetoothAdapter.getBondedDevices();

            if (mPairedDevices.size() > 0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("장치 선택");

                mListPairedDevices = new ArrayList<String>();
                for (BluetoothDevice device : mPairedDevices) {
                    mListPairedDevices.add(device.getName());
                    //mListPairedDevices.add(device.getName() + "\n" + device.getAddress());
                }
                final CharSequence[] items = mListPairedDevices.toArray(new CharSequence[mListPairedDevices.size()]);
                mListPairedDevices.toArray(new CharSequence[mListPairedDevices.size()]);

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        connectSelectedDevice(items[item].toString());
                    }
                });
                AlertDialog alert = builder.create();
                alert.show();
            } else {
                Toast.makeText(getApplicationContext(), "페어링된 장치가 없습니다.", Toast.LENGTH_LONG).show();
            }
        }
        else {
            Toast.makeText(getApplicationContext(), "블루투스가 비활성화 되어 있습니다.", Toast.LENGTH_SHORT).show();
        }
    }
    void connectSelectedDevice(String selectedDeviceName) {
        for(BluetoothDevice tempDevice : mPairedDevices) {
            if (selectedDeviceName.equals(tempDevice.getName())) {
                mBluetoothDevice = tempDevice;
                break;
            }
        }
        try {
            mBluetoothSocket = mBluetoothDevice.createRfcommSocketToServiceRecord(BT_UUID);
            mBluetoothSocket.connect();
            mThreadConnectedBluetooth = new ConnectedBluetoothThread(mBluetoothSocket);
            mThreadConnectedBluetooth.start();
            mBluetoothHandler.obtainMessage(BT_CONNECTING_STATUS, 1, -1).sendToTarget();
            Toast.makeText(getApplicationContext(), "블루투스가 연결되었습니다.", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "블루투스 연결 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
        }
    }
    private class ConnectedBluetoothThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedBluetoothThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "소켓 연결 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = mmInStream.available(); // 버퍼에 수신된 데이터가 있는지 확인
                    if (bytes != 0) { // 0을 반환할 때까지 데이터를 읽어들임
                        buffer = new byte[1024]; // 버퍼를 매번 초기화 시켜 줌
                        SystemClock.sleep(200);
                        bytes = mmInStream.available();
                        bytes = mmInStream.read(buffer, 0, bytes); // 입력 스트림으로부터 매개값으로 주어진 바이트 배열의 길이[1024]만큼 바이트를 읽고 buffer에 저장. 배열의 길이보다 실제 읽은 바이트 수가 적으면 읽을 수 있을 만큼만 읽음
                        mBluetoothHandler.obtainMessage(BT_MESSAGE_READ, bytes, -1, buffer).sendToTarget(); // 위에 정의된 mBluetoothHandler = new Handler() ~ 이벤트 핸들러를 호출 시킴 (buffer 내용을 인자로)
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }
        public void write(String str) {
            byte[] bytes = str.getBytes();
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "데이터 전송 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
            }
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "소켓 해제 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show();
            }
        }
    }



}