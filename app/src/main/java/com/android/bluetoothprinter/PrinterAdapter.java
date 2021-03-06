package com.android.bluetoothprinter;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;

import static com.android.bluetoothprinter.PrintBean.PRINT_TYPE;


/**
 * 类说明:蓝牙设备的适配器
 * shi-bash-cmd  2020/02/06
 */
class PrinterAdapter extends BaseAdapter {

    private static String TAG = PrinterAdapter.class.getSimpleName();

    private ArrayList<PrintBean> mBluetoothDevicesDatas;

    private Context mContext;
    //蓝牙适配器
    private BluetoothAdapter mBluetoothAdapter;
    //蓝牙socket对象
    private BluetoothSocket mmSocket;
    private UUID uuid;
    //打印的输出流
    private static OutputStream outputStream = null;
    //搜索弹窗提示
    ProgressDialog progressDialog = null;
    private final int exceptionCod = 100;

    private boolean isStartPrint;

    private static PrintBean dataBean;

    //在打印异常时更新ui
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == exceptionCod) {
                Toast.makeText(mContext, "打印发送失败，请稍后再试", Toast.LENGTH_SHORT).show();
                if (progressDialog != null) {
                    progressDialog.dismiss();
                }
            }
        }
    };

    /**
     * 主页面开启蓝牙后连接设备操作
     * @param context                上下文
     * @param mBluetoothDevicesDatas 设备列表
     */
    public PrinterAdapter(Context context, ArrayList<PrintBean> mBluetoothDevicesDatas) {
        this.mBluetoothDevicesDatas = mBluetoothDevicesDatas;
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    }

    public int getCount() {
        return mBluetoothDevicesDatas.size();
    }

    @Override
    public Object getItem(int position) {
        return position;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    //这里只设置与打印设备的连接
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        convertView = LayoutInflater.from(mContext).inflate(R.layout.item, null);
        View icon = convertView.findViewById(R.id.icon);
        TextView name = (TextView) convertView.findViewById(R.id.name);
        TextView address = (TextView) convertView.findViewById(R.id.address);
        TextView start = (TextView) convertView.findViewById(R.id.start);

        dataBean = mBluetoothDevicesDatas.get(position);
        icon.setBackgroundResource(dataBean.getTypeIcon());
        name.setText(dataBean.name);
        address.setText(dataBean.isConnect ? "已连接" : "未连接");
        start.setText(dataBean.getDeviceType(start));

        //点击连接与打印
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    //如果已经连接并且是打印机
                    if (dataBean.isConnect && dataBean.getType() == PRINT_TYPE) {
                        if (mBluetoothAdapter.isEnabled()) {
//                            new ConnectThread(mBluetoothAdapter.getRemoteDevice(dataBean.address)).start();
                            isStartPrint = true;
                            Toast.makeText(mContext, "设备已连接", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(mContext, "蓝牙没有打开", Toast.LENGTH_SHORT).show();
                        }
                        //没有连接
                    } else {
                        //是打印机
                        if (dataBean.getType() == PRINT_TYPE) {
                            setConnect(mBluetoothAdapter.getRemoteDevice(dataBean.address), position);
                            //不是打印机 进行其它蓝牙设备操作
                        } else {
                            Toast.makeText(mContext, "该设备不是打印机", Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        return convertView;
    }

    /**
     * 完成连接Socket才能开始打印
     */
    private void connectPrinterSocket() {
        if (isStartPrint) {
            try {
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(dataBean.address);
                mmSocket = device.createRfcommSocketToServiceRecord(uuid);
                mmSocket.connect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 匹配设备
     *
     * @param device 设备
     */
    private void setConnect(BluetoothDevice device, int position) {
        try {
            Method createBondMethod = BluetoothDevice.class.getMethod("createBond");
            createBondMethod.invoke(device);
            mBluetoothDevicesDatas.get(position).setConnect(true);
            notifyDataSetChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 发送数据 打印文本
     */
    public void send(String sendData) {
        try {
            byte[] data = sendData.getBytes("gbk");
            outputStream.write(data, 0, data.length);
            outputStream.flush();
            outputStream.close();
            progressDialog.dismiss();
        } catch (IOException e) {
            e.printStackTrace();
            handler.sendEmptyMessage(exceptionCod); // 向Handler发送消息,更新UI

        }
    }

    /**
     * 打印图片接口
     * @param bitmap bitmap转换成byte[] 类型的数据
     */
    public void startPrintImage(Bitmap bitmap) {
        if (mmSocket == null && dataBean.getType() == PRINT_TYPE) {
            connectPrinterSocket();
        } else {
            Toast.makeText(mContext, "未找到打印设备", Toast.LENGTH_SHORT).show();
        }
        try {
            outputStream = mmSocket.getOutputStream();
            //这里是打印操作
            imageInit(outputStream, bitmap);
        } catch (IOException e) {
            e.printStackTrace();
            handler.sendEmptyMessage(exceptionCod); // 向Handler发送消息,更新UI
        }
    }

    /**
     * 打印文本接口
     * @param sendData 传入的String
     */
    public void startPrintText(String sendData) {
        if (mmSocket == null && dataBean.getType() == PRINT_TYPE) {
            connectPrinterSocket();
        } else {
            Toast.makeText(mContext, "未找到打印设备", Toast.LENGTH_SHORT).show();
        }
        try {
            outputStream = mmSocket.getOutputStream();
            byte[] data = sendData.getBytes("gbk");
            outputStream.write(data, 0, data.length);
            //切纸
            outputStream.write(new byte[]{0x0a, 0x0a, 0x1d, 0x56, 0x01});
            outputStream.flush();
            outputStream.close();
            progressDialog.dismiss();
        } catch (IOException e) {
            e.printStackTrace();
            handler.sendEmptyMessage(exceptionCod); // 向Handler发送消息,更新UI

        }
    }

    /**
     * 连接为客户端
     * 线程执行打印工作
     */
    private class ConnectThread extends Thread {
        public ConnectThread(BluetoothDevice device) {
            try {
                mmSocket = device.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            super.run();
            //取消的发现,因为它将减缓连接
            mBluetoothAdapter.cancelDiscovery();
            try {
                //连接socket
                mmSocket.connect();
                //连接成功获取输出流
                outputStream = mmSocket.getOutputStream();

                //打印方法 后续增加多种打印方式
//                send(mPrintContent);
            } catch (Exception connectException) {
                Log.e("test", "连接失败");
                connectException.printStackTrace();
                //异常时发消息更新UI
                Message msg = new Message();
                msg.what = exceptionCod;
                // 向Handler发送消息,更新UI
                handler.sendMessage(msg);

                try {
                    mmSocket.close();
                } catch (Exception closeException) {
                    closeException.printStackTrace();
                }
                return;
            }
        }
    }

    /**
     * 图片数据处理及打印操作
     * @param outputStream
     * @param bitmap
     */
    private void imageInit(OutputStream outputStream, Bitmap bitmap) {
        try {
            PrintUtil printUtil = new PrintUtil();
            printUtil.setOutputStream(outputStream);
            printUtil.selectCommand(PrintUtil.RESET);
            printUtil.selectCommand(PrintUtil.NORMAL);

            Bitmap tempBitmap = printUtil.convertGreyImgByFloyd(bitmap);
            byte[] imgData = printUtil.bitmap2Bytes(tempBitmap, Bitmap.CompressFormat.JPEG);

            outputStream.write(imgData, 0, imgData.length);
            //切纸
            outputStream.write(new byte[]{0x0a, 0x0a, 0x1d, 0x56, 0x01});
            outputStream.flush();
            outputStream.close();
            progressDialog.dismiss();
        } catch (Exception e) {
            e.printStackTrace();
            handler.sendEmptyMessage(exceptionCod); // 向Handler发送消息,更新UI
        }
    }

    /**
     * 结束工作时收尾操作
     */
    public void releasePrint(){
        mBluetoothAdapter = null;
        if(mmSocket != null){
            try {
                mmSocket.close();
                mmSocket = null;
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG,"releasePrint()，IOException ：",e);
                mmSocket = null;
            }
        }
    }

}