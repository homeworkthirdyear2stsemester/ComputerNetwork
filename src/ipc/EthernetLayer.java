package ipc;

import java.util.ArrayList;

public class EthernetLayer implements BaseLayer {

    public int nUpperLayerCount = 0;
    public String pLayerName = null;
    public BaseLayer p_UnderLayer = null;
    public ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<>();
    private _ETHERNET_Frame ethernetHeader = new _ETHERNET_Frame();

    public EthernetLayer(String pName) {
        this.pLayerName = pName;
    }

    public void setDestNumber(byte[] array) {
        this.ethernetHeader.enet_dstaddr.setAddrData(array);
    }//dst 정보 저장

    public void setSrcNumber(byte[] array) {
        this.ethernetHeader.enet_srcaddr.setAddrData(array);
    }//src 정보 저장

    public byte ethernetHeaderGetType(int index) {
        return this.ethernetHeader.enet_type[index];
    }

    private class _ETHERNET_ADDR {
        private byte[] addr = new byte[6];

        public _ETHERNET_ADDR() {
            for (int indexOfAddr = 0; indexOfAddr < addr.length; ++indexOfAddr) {
                this.addr[indexOfAddr] = (byte) 0x00;
            }
        }

        public byte getAddrData(int index) {
            return this.addr[index];
        }

        public void setAddrData(byte[] data) {
            this.addr = data;
        }
    }

    private class _ETHERNET_Frame {
        _ETHERNET_ADDR enet_dstaddr;//dst 정보
        _ETHERNET_ADDR enet_srcaddr;//src 정보
        byte[] enet_type;
        byte[] enet_data;

        public _ETHERNET_Frame() {
            this.enet_dstaddr = new _ETHERNET_ADDR();
            this.enet_srcaddr = new _ETHERNET_ADDR();
            this.enet_type = new byte[2];
            this.enet_type[0] = 0x08;
            this.enet_type[1] = 0x00;
            this.enet_data = null;
        }
    }

    private byte[] etherNetDst() {
        return this.ethernetHeader.enet_dstaddr.addr;
    }

    private byte[] etherNetSrc() {
        return this.ethernetHeader.enet_srcaddr.addr;
    }

    @Override
    public String GetLayerName() {
        return pLayerName;
    }

    @Override
    public BaseLayer GetUnderLayer() {
        if (p_UnderLayer == null)
            return null;
        return p_UnderLayer;
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
        this.p_UnderLayer = pUnderLayer;
    }

    @Override
    public void SetUpperLayer(BaseLayer pUpperLayer) {
        if (pUpperLayer == null)
            return;
        this.p_aUpperLayer.add(nUpperLayerCount++, pUpperLayer);//layer추가
        // nUpperLayerCount++;
    }

    @Override
    public void SetUpperUnderLayer(BaseLayer pUULayer) {
        this.SetUpperLayer(pUULayer);
        pUULayer.SetUnderLayer(this);
    }

    @Override
    public synchronized boolean Send(byte[] input, int length) {
        byte is_checked = input[0];
        byte[] headerAddedArray = new byte[length + 14];
        int index = 0;
        byte[] src_mac = this.ethernetHeader.enet_srcaddr.addr;//내 mac주소
        byte[] dst_mac = ARPLayer.getMacAddress(src_mac);//ip에 따른 mac주소 가져오기

        if (is_checked == 0x06 && input[8] == 0x01) {//arp요청
            while (index < 6) {//브로드캐스트
                headerAddedArray[index] = (byte) 0xff;
                index += 1;
            }
            headerAddedArray[13] = (byte) 0x06;
        } else if (is_checked == 0x06 && input[8] == 0x02) {//arp 응답
            while (index < 6) {//요청온 주소
                headerAddedArray[index] = dst_mac[index];
                index += 1;
            }
            headerAddedArray[13] = (byte) 0x06;
        } else if (is_checked == 0x08) {//ip
            while (index < 6) {//해당 mac으로 보냄
                headerAddedArray[index] = dst_mac[index];
                index += 1;
            }
            headerAddedArray[13] = this.ethernetHeader.enet_type[1];
        }

        while (index < 12) { // 나의 mac주소
            headerAddedArray[index] = this.ethernetHeader.enet_srcaddr.getAddrData(index - 6);//내 mac주소
            index += 1;
        }
        headerAddedArray[12] = this.ethernetHeader.enet_type[0];
        System.arraycopy(input, 0, headerAddedArray, 14, length);

        return this.GetUnderLayer().Send(headerAddedArray, headerAddedArray.length);
    }

    @Override
    public synchronized boolean Receive(byte[] input) {
        if (!this.isMyAddress(input) && (this.isBoardData(input) || this.isMyConnectionData(input))
                && input[12] == 0x08 && input[13] == 0x00) {//브로드이거나 나한테
            byte[] removedHeaderData = this.removeCappHeaderData(input);
            if (removedHeaderData[0] == 0x08) {//ip
                return this.GetUpperLayer(0).Receive(removedHeaderData); // IP Layer
            } else if (removedHeaderData[0] == 0x06) {//arp
                return this.GetUpperLayer(1).Receive(removedHeaderData); // ARP Layer
            }
        }
        return false;
    }

    private byte[] removeCappHeaderData(byte[] input) {//header 제거
        byte[] removeCappHeader = new byte[input.length - 14];
        for (int index = 0; index < removeCappHeader.length; index++) {
            removeCappHeader[index] = input[index + 14];
        }

        return removeCappHeader;
    }

    /*
     * @param  myAddressData : mac주소 byte 배열
     * @param  inputFrameData : header를 재거하지 않은 배열
     * @param  inputDataStartIndex : src : 6, dst : 0을 넣으면 된다 -> 코드 재사용 때문에 사용
     * @return : 비교해서 동일 : true, 다름 : false
     */
    private boolean checkTheFrameData(byte[] myAddressData, byte[] inputFrameData, int inputDataStartIndex) {// add prarmeter 사용,
        for (int index = inputDataStartIndex; index < inputDataStartIndex + 6; index++) {
            if (inputFrameData[index] != myAddressData[index - inputDataStartIndex]) {
                return false;
            }
        }
        return true;
    }

    private boolean isBoardData(byte[] inputFrameData) {
        byte[] boardData = new byte[6];
        for (int index = 0; index < 6; index++) {
            boardData[index] = (byte) 0xFF;
        }
        return this.checkTheFrameData(boardData, inputFrameData, 0);
    }// 브로드 케스트인지 check

    private boolean isMyConnectionData(byte[] inputFrameData) {
        byte[] srcAddr = this.etherNetSrc();
        byte[] dstAddr = this.etherNetDst();
        return this.checkTheFrameData(dstAddr, inputFrameData, 6)
                && this.checkTheFrameData(srcAddr, inputFrameData, 0);
    }// 지금 받은 frame이 나랑 연결된 mac주소인지 판별

    private boolean isMyAddress(byte[] inputFrameData) {
        byte[] srcAddr = this.etherNetSrc();
        return this.checkTheFrameData(srcAddr, inputFrameData, 6);
    }// loop back일 경우 true, 다른 곳에서 온 frame : false
}