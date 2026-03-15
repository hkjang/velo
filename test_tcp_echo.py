import socket
import struct
import sys

def test_tcp_echo(host, port, message):
    try:
        # Create a TCP socket
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(5.0)
        print("Connecting to {0}:{1}...".format(host, port))
        s.connect((host, port))
        
        # Prepare the request: [4 bytes length] [payload]
        payload = message.encode('utf-8')
        length = len(payload)
        request = struct.pack('>I', length) + payload
        
        print("Sending: {0} ({1} bytes)".format(message, length))
        s.sendall(request)
        
        # Read the response: [4 bytes length] [payload]
        header = s.recv(4)
        if not header or len(header) < 4:
            print("No complete response header received.")
            s.close()
            return False
        
        resp_length = struct.unpack('>I', header)[0]
        print("Expected response length: {0}".format(resp_length))
        
        resp_payload = b""
        while len(resp_payload) < resp_length:
            chunk = s.recv(resp_length - len(resp_payload))
            if not chunk:
                break
            resp_payload += chunk
        
        received_message = resp_payload.decode('utf-8')
        print("Received: {0}".format(received_message))
        
        s.close()
        
        if received_message == message:
            print("SUCCESS: Echo matches!")
            return True
        else:
            print("FAILURE: Echo mismatch!")
            return False
            
    except Exception as e:
        print("Error: {0}".format(e))
        return False

if __name__ == "__main__":
    host = "127.0.0.1"
    port = 9090
    msg = "Hello Velo TCP!"
    if len(sys.argv) > 1:
        msg = sys.argv[1]
    
    success = test_tcp_echo(host, port, msg)
    sys.exit(0 if success else 1)
