# TAK BLE

This is a plugin developed to allow for communication of CoT and files over Bluetooth Low Energy in ATAK.

In order to use the plugin, the TAK Server connection must be properly set up first.

## Configuration of TAK Server Connection

Ensure that your TAK Server connection is set to connect to 127.0.0.1, port 8089, with TCP. The TAK BLE plugin creates a "fake" TAK Server to intercept messages from ATAK to send over BLE, so it requires this connection to be set to properly exchange information.

## Setup of Connection

To use the plugin, you should have two instances of ATAK running with the TAK BLE plugin installed and loaded and the TAK Server connection configured as specified above.

Open the TAK BLE plugin on both phones, and press on the "Start Scan" button. This will make the TAK BLE plugin search for nearby devices that also have the TAK BLE plugin, in order to exchange information with them (currently communication is just supported between two instances of ATAK).

If the scan successfully finds another device and establishes a connection, you should see the "Remote device connected:" label change to "CONNECTED" on the device being connected to. You should also see logs in the "Peripheral Logs" section saying "Found data transfer characteristic, trying to subscribe to notifications...".

## Exchanging CoT Information over BLE

Once the connection is established both ways, you can exchange CoT over BLE using the chat functionality of ATAK (opening the Contacts page and sending a message to "All Chat Rooms" or directly to the other phone's contact, which should show in the Contacts section since CoT containing track information should be being exchanged over BLE automatically).

You can also try exchanging CoT information by dropping a point, and then sending that point to the other device's contact, again using functionality built into ATAK.

## Exchanging Files over BLE

You can exchange files over BLE by using the built in "Data Packages" tool in ATAK, which allows you to create a Data Package and send it to your TAK Server connection, and also download available data packages from your TAK Server connection.

To do the file exchange over BLE, you can use the same process by which you would exchange files in regular ATAK:

1. On device A, you can create a data package (which may contain shape data, files, overlays, etc).
2. On device A, you can press the "Send" button for the data package and send it to the TAK Server connection (which should be the configuration mentioned in the "Configuration of TAK Server Connection" section of this README).
3. On device B, you can press the download button on the Data Package page (which is next to the button for creating a Data Package), and select the TAK Server connection you configured to download files from it - this will retrieve a list of available files from device A over BLE.
4. On Device B, you should see a query for available data packages load, and then see a page displaying available data packages - you can select the data package of interest and press the "Download" button. This will download the data package from device A over BLE.
