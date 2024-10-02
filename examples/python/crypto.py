"""
Usage of built-in crypto functions (ScriptCryptoHelper)
"""

import json
from base64 import b64encode, b64decode


CLIENT_PRIV_KEY = """
-----BEGIN PRIVATE KEY-----
MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCYGaqn86RVCoUP
mu8YoosxUDDvd47v7va7/u1C34B/BIyWq0v7MG8Y/HjkTohrRahiwNydLz66xXQL
y5zRFTHwbwZ6MXXVn0Qo4f9MqSWveDW5ihXM+Q1yriAMwG6D512pu7Suw0b82Hd0
2xLY05QJ42YGR3T2DUJXL4U1K+xsu+XN4ylCCDcERgjYWoQ+Ir/IjvVYhPeBYTof
2obddOEfwaBuWbadfkhQPCCIyTxAdxxDLEbEpnFLrF3Nva5SLn3uCEGaWG4HvdKM
yJ9TmBj5TtVbLHVhTYYhtsaMjaqnlJerp4UnoJ+asbbjzkP+tREeLQtXwS2wndp+
K2T9bA7bAgMBAAECggEACiT0F92NIUrhUwgfWEJHDFPv35jWxLPoauN2yZYEiPQx
uD7Wg3tYfY8hNQDz4ku0DloUnLsw8N4IflznKZ7DROjywqWX2VaVAjEIiQFjDQ/0
bVqDV7doqTRp2M/gzxVYTuDBDULi8iwx025lFGcQIZS0EkkjyOFbglseBEzYqOvI
8gb14je1FkadssdSY8AJY/pQ2OdfM+p7tf8TodrlsEzuvRdFX7E+4rI2woyEzSYu
D4uNH0CiC1OhWB2ckFoL/tKH3D95jAY1WAdM295xkvLQpZRkENlrvYrtHvwvktXF
1yqzObtijEeJhEqbm53KTdFU0ZFpFtlZnj1/e5MrEQKBgQDMslvoBVuFiJgCdT+R
5VOn3BoBh0B8XF++xwjr5F8HwAJw1hdf1oDJMmFmJ3npFBMoWXA7gPfX8gpu91ys
x575xrXo/mtOZUcN+En/n4MNDuWDN5AjsLREPexrvIZ5rYP+BZoh2UkeKjWVot98
XQJtTT/4umcGjhCunI4ayjedkwKBgQC+OKReRrawyALVKsMGyGoOQlWg/Vsy+N0x
DgpDIA998COCpH/O1/BI8d04QZu8Q3eeiYfDLcrAtcdgiE+DVGh9Xg1BMeYbgXIt
lAwDYEEBSruS5FrrapTjigaGCnX4DDNZlDdMrGyw1yY5Cs8KvLua/s5YjPCTPGmm
5dMSQ3LWmQKBgHuoV/svmV1u6h26BQA3ILVsMs2vjlZSW4jdplcS7BG7ff36Z75+
z+g7pjlXKb+TYAtlFHbt70umLYVhq7u5ECHmWCh74glHB4i58MIa88lksWP2of3d
ltkO648eIcLJ/s3rRnSiVhiB+UL/VLFFYtzy6O1ydiCwnAVQEEzA0p4/AoGAFnTn
ar3caYhjVTkkJxPX+XD5XPUsJBtfOaBXs88AJTUJbC3xbMDvfB0Zqb+NHC+22n+Q
CInKau/K5umQwYdggpRs6ipy6QJiMWFN/cQKSJXDCTduSGafxzEPThnEDZGbKlMm
KCYe+s2blJZjFPhtCYJVZ/zTlf5G1s5BGeHel9kCgYBjhvI9d2yqZZ66fGkQDhFj
244k1h2rPMq19/AiNjb6WgWZnEhE0YJnE+AxfHEw09t2hPrDPuH+XDsBKhzUMVXo
hBFyUITkrLhyNHY7RFG06cwDYNK5PAxzC8Jy5lZCB8CoPDMeClwWz2+SNEd1CmKj
izvvI8GSCdVzqugOIS6ltg==
-----END PRIVATE KEY-----
"""

CLIENT_PUB_KEY = """
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmBmqp/OkVQqFD5rvGKKL
MVAw73eO7+72u/7tQt+AfwSMlqtL+zBvGPx45E6Ia0WoYsDcnS8+usV0C8uc0RUx
8G8GejF11Z9EKOH/TKklr3g1uYoVzPkNcq4gDMBug+ddqbu0rsNG/Nh3dNsS2NOU
CeNmBkd09g1CVy+FNSvsbLvlzeMpQgg3BEYI2FqEPiK/yI71WIT3gWE6H9qG3XTh
H8Ggblm2nX5IUDwgiMk8QHccQyxGxKZxS6xdzb2uUi597ghBmlhuB73SjMifU5gY
+U7VWyx1YU2GIbbGjI2qp5SXq6eFJ6CfmrG2485D/rURHi0LV8EtsJ3afitk/WwO
2wIDAQAB
-----END PUBLIC KEY-----
"""


class RSACrypto:
    """
    In this example, information is sent back to the client,
    encrypted using the client's public RSA key. The script
    intercepts and modifies the data bound for the client.
    """

    RESP_FILTER = """
    (and
        (has-header "X-Encrypted-Data")
        (path-contains "/rsa-example")
    )
    """

    def on_response(self, resp):
        dec_rsa = helpers.getCryptoHelper().newCipher("RSA/ECB/PKCS1Padding")
        dec_rsa.setKey(CLIENT_PRIV_KEY)

        enc_rsa = helpers.getCryptoHelper().newCipher("RSA/ECB/PKCS1Padding")
        enc_rsa.setKey(CLIENT_PUB_KEY)

        data = b64decode(
            resp.header("X-Encrypted-Data").value()
        )

        # `decrypt` returns a Java byte array, convert this to python bytes object
        decrypted = bytes(dec_rsa.decrypt(data))
        obj = json.loads(
            decrypted.decode()
        )
        print(f"Decrypted data: {obj}")

        obj["authorized"] = True

        # `encrypt` returns a Java byte array, convert this to python bytes object
        encrypted = bytes(enc_rsa.encrypt(json.dumps(obj).encode()))

        data = b64encode(encrypted).decode()
        return resp.withUpdatedHeader("X-Encrypted-Data", data)


addons = [ RSACrypto() ]
