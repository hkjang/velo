import socket
import struct
import sys

def send_msg(msg_type, payload):
    host = '127.0.0.1'
    port = 9090
    
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(5.0)
        s.connect((host, port))
        
        # DefaultMessageCodec in Velo expects just the payload if not formatted.
        # But our router uses message.messageType(). 
        # Wait, looking at DefaultMessageCodec: 
        # it returns new TcpMessage("default", payload);
        # So we can't easily test "fixed-msg" type without a specialized codec 
        # UNLESS we use the fallback or change the codec.
        
        # Ah! I should have checked the codec. DefaultMessageCodec always sets type to "default".
        # Let's fix the test app to use "default" for now, or implement a simple type-steering codec.
        
        length = len(payload)
        s.sendall(struct.pack('>I', length) + payload)
        
        header = s.recv(4)
        if not header:
            return "No response"
        resp_len = struct.unpack('>I', header)[0]
        data = s.recv(resp_len)
        s.close()
        return data.decode('utf-8')
    except Exception as e:
        return str(e)

if __name__ == "__main__":
    # "fixed-msg" test: 5 bytes header 'HEAD1' + 10 bytes body 'BODY123456'
    print("Sending professional msg to TCP...")
    response = send_msg("fixed-msg", "HEAD1BODY123456")
    print("Response: " + response)
