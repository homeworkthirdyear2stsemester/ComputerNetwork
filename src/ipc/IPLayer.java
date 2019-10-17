package ipc;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class IPLayer implements BaseLayer {
    public int nUpperLayerCount = 0;
    public int nUnderLayerCount = 0;
    public String pLayerName = null;
    public ArrayList<BaseLayer> p_aUnderLayer = new ArrayList<BaseLayer>();
    public ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();
    private _IP_Header ip_header = new _IP_Header();
    // 1 : ARP, 0 : Ethernet

    //IP주소 변수
    private byte[] src_ip_addr;


    public IPLayer(String pName) {
        pLayerName = pName;
        src_ip_addr = GetIpAddress();
        SetIpSrcAddress(src_ip_addr);//내 IP주소 세팅
    }

    //내 IP주소 리턴
    public byte[] get_IP_addr() {
        return src_ip_addr;
    }

    private byte[] RemoveCappHeader(byte[] input, int length) {

        byte[] temp = new byte[length - 21];
        System.arraycopy(input, 21, temp, 0, length - 21);
        return temp;
    }


    public boolean Send(byte[] input, int length) {

        this.ip_header.ip_dstaddr.addr = new byte[4]; //헤더 주소 초기화
        this.ip_header.ip_srcaddr.addr = new byte[4];

        SetIpSrcAddress(src_ip_addr);
        SetIpDstAddress(((ARPDlg) this.GetUpperLayer(1)).TargetIPAddress);
        byte[] temp = ObjToByte21(this.ip_header, input, length); //multiplexing


        if (((ARPLayer) this.GetUnderLayer(1)).containMacAddress(this.ip_header.ip_dstaddr.addr))//목적지 IP주소가 캐싱되어있으면
            return ((EthernetLayer) this.GetUnderLayer(0)).Send(temp, length + 21);//데이터이므로 Ethernet Layer로 전달
        else return ((ARPLayer) this.GetUnderLayer(1)).Send(temp, length + 21);    //아니면 ARP 요청이므로 ARP Layer로 전달


    }

    private byte[] ObjToByte21(_IP_Header ip_header, byte[] input, int length) { // 헤더 추가부분

        byte[] buf = new byte[length + 21];

        buf[0] = ip_header.is_checked;
        buf[1] = ip_header.ip_verlen;
        buf[2] = ip_header.ip_tos;
        buf[3] |= (byte) ((((short) (length + 20)) >> 8) & 0xFF);
        buf[4] |= (byte) (((short) (length + 20)) & 0xFF);

        buf[5] |= (byte) ((ip_header.ip_id >> 8) & 0xFF);
        buf[6] |= (byte) (ip_header.ip_id & 0xFF);

        buf[7] |= ((ip_header.ip_fragoff >> 8) & 0xFF);
        buf[8] |= (ip_header.ip_fragoff & 0xFF);

        buf[9] = ip_header.ip_ttl;
        buf[10] = ip_header.ip_proto;

        buf[11] |= ((ip_header.ip_cksum >> 8) & 0xFF);
        buf[12] |= (ip_header.ip_cksum & 0xFF);


        System.arraycopy(ip_header.ip_srcaddr.addr, 0, buf, 13, 4);
        System.arraycopy(ip_header.ip_dstaddr.addr, 0, buf, 17, 4);
        System.arraycopy(input, 0, buf, 21, length);

        return buf;
    }

    public synchronized boolean Receive(byte[] input) {

        // IP 타입 체크 ip_verlen : ip version 0x04      ip_header.ip_tos : type of service 0x00
        if (this.ip_header.ip_verlen != input[1] || this.ip_header.ip_tos != input[2]) {
            return false;
        } // ip 버전이 4인거만 받았다 -> 4, 6중에 4만 받음

        int packet_tot_len = ((input[3] << 8) & 0xFF00) + input[4] & 0xFF; //수신된 패킷의 전체 길이

        for (int addr_index_count = 0; addr_index_count < 4; addr_index_count++) { // 내 주소가 아닐 경우 무조건 proxy를 보내는 걸 한다.
            if (this.ip_header.ip_srcaddr.addr[addr_index_count] != input[17 + addr_index_count]) {  //수신한 데이터의 목적지 IP주소가 나의 IP주소와 일치하는지 확인
                return this.GetUnderLayer(0).Send(input, packet_tot_len);  //일치하지 않으면 프록시 기능으로 대신 전달해야 하는 데이터라고 인지하여 Ethernet Layer에 전달
                //ethernet send에서 상대 맥주소 테이블에서 찾을때 ARP테이블이랑 proxy테이블 둘다 찾아봐야할듯?
                //프록시 연결이 되고 데이터가 최종 목적지에 도착하면 최종목적지 arp테이블에 주소가 반영되는지?
            }
        }// proxy arp

        //일치하면 최종 목적지가 자신이므로 de-multiplex하고 상위 레이어로 올림
        if (input[10] == 0x06) {//IP Protocol 0x06 :TCP인지 판별
            return this.GetUpperLayer(0).Receive(RemoveCappHeader(input, packet_tot_len));
        }

        return false;
    }

    @Override
    public String GetLayerName() {
        return pLayerName;
    }


    public BaseLayer GetUnderLayer(int nindex) {
        if (nindex < 0 || nindex > nUnderLayerCount || nUnderLayerCount < 0)
            return null;
        return p_aUnderLayer.get(nindex);

    }

    @Override
    public BaseLayer GetUpperLayer(int nindex) {
        if (nindex < 0 || nindex > nUpperLayerCount || nUpperLayerCount < 0)
            return null;
        return p_aUpperLayer.get(nindex);
    }

    @Override
    public void SetUnderLayer(BaseLayer pUnderLayer) {
        if (pUnderLayer == null)
            return;
        this.p_aUnderLayer.add(nUnderLayerCount++, pUnderLayer);
    }

    @Override
    public void SetUpperLayer(BaseLayer pUpperLayer) {
        // TODO Auto-generated method stub
        if (pUpperLayer == null)
            return;
        this.p_aUpperLayer.add(nUpperLayerCount++, pUpperLayer);

    }

    @Override
    public void SetUpperUnderLayer(BaseLayer pUULayer) {
        this.SetUpperLayer(pUULayer);
        pUULayer.SetUnderLayer(this);
    }

    // src IP주소 세팅
    public void SetIpSrcAddress(byte[] srcAddress) {
        ip_header.ip_srcaddr.addr = srcAddress;
    }

    // dst IP주소 세팅
    public void SetIpDstAddress(byte[] dstAddress) {
        ip_header.ip_dstaddr.addr = dstAddress;

    }

    private byte[] GetIpAddress() {

        InetAddress local = null;
        try {
            local = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        String[] hostAddressInString = local.getHostAddress().split("[.]");

        byte[] temp = new byte[4];
        for (int i = 0; i < 4; i++) {
            int eachAddress = Integer.parseInt(hostAddressInString[i]);
            temp[i] = (byte) (eachAddress & 0xFF);
        }

        return temp;
    }

    // Header 자료구조
    private class _IP_Header {
        byte is_checked; // ARP면 0x06, 일반 데이터면 0x08  index 0
        byte ip_verlen; // ip version (1byte)   index 1
        byte ip_tos; // type of service (1byte) index 2
        short ip_len; // total packet length (2byte) index 3~4
        short ip_id; // datagram Identification(2byte) index 5~6
        short ip_fragoff;// fragment offset (2byte)   index 7~8
        byte ip_ttl; // time to live in gateway hops (1byte) index 9
        byte ip_proto; // IP protocol (1byte) index 10     TCP:6  UDP:17
        short ip_cksum; // header checksum (2byte) index 11 12
        // 0~11 index

        _IP_ADDR ip_srcaddr;// source IP address (4byte) 13~16 index
        _IP_ADDR ip_dstaddr;// destination IP address (4byte) 17~20 index

        // byte ip_data[]; //variable length data (variable)

        private _IP_Header() {
            this.is_checked = 0x08;
            this.ip_verlen = 0x04;
            this.ip_tos = 0x00;
            this.ip_len = 0;
            this.ip_id = 0;
            this.ip_fragoff = 0;
            this.ip_ttl = 0x00;
            this.ip_proto = 0x06;
            this.ip_cksum = 0;
            this.ip_srcaddr = new _IP_ADDR();
            this.ip_dstaddr = new _IP_ADDR();

        }

        // 헤더의 IP주소 자료구조
        private class _IP_ADDR {
            private byte[] addr = new byte[4];

            public _IP_ADDR() {
                this.addr[0] = 0x00;
                this.addr[1] = 0x00;
                this.addr[2] = 0x00;
                this.addr[3] = 0x00;
            }
        }
    }

    @Override
    public BaseLayer GetUnderLayer() {
        // TODO Auto-generated method stub
        return null;
    }
}