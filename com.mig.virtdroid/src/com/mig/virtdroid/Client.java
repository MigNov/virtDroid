package com.mig.virtdroid;

/**
 * VirtDroid XMLRPC Client
 *
 * Author: Michal Novotny <mignov@gmail.com>
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.xmlrpc.android.XMLRPCClient;
import org.xmlrpc.android.XMLRPCException;
import org.xmlrpc.android.XMLRPCFault;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.method.ScrollingMovementMethod;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class Client extends Activity {
	private XMLRPCClient client;
	private URI uri;
	private TextView textView;
	private ListView listView;
	private ArrayList<String> dNames;
	private ArrayList<String> cNames;
	private ArrayList<String> cUris;
	private String myUri = null;
	private Boolean changingUri = true;
	private Boolean phaseThree = false;
	private Boolean domainList = false;
	private Boolean inAboutDlg = false;
	private long interval = -1;
	private Thread thread = null;
	private String domName = null;

	private boolean isServerReachable(String str) {
		URL url;
		try {
			url = new URL(str);
		} catch (MalformedURLException e1) {
			return false;
		}

		try {
			return InetAddress.getByName( url.getHost() ).isReachable(0);
		} catch (UnknownHostException e) {
			/* Do nothing */
		} catch (IOException e) {
			/* Do nothing */
		}

		return false;
	}

	@Override
    protected void onCreate(Bundle savedInstanceState) {
		  super.onCreate(savedInstanceState);
		  setContentView(R.layout.main);

		  listView = (ListView) findViewById(R.id.list_view);
          textView = (TextView) findViewById(R.id.text_view);

  		  textView.setMovementMethod(new ScrollingMovementMethod());
          registerForContextMenu(listView);

          /* Set new information if we have them from the QR Code/Browser view */
  		  try {
  			String address = getIntent().getData().getQueryParameter("address");
  			String apikey  = getIntent().getData().getQueryParameter("apikey");
              SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
              SharedPreferences.Editor edit = sharedPrefs.edit();
              edit.putString("address", address);
              edit.putString("apikey", apikey);
              edit.commit();

              Toast.makeText(getApplicationContext(), "New information has been set", Toast.LENGTH_LONG).show();
  	  	  } catch (Exception e) {
  			// Do nothing
  		  }

    	  SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
          String uristr = sharedPrefs.getString("address", "NULL");

          if (sharedPrefs.getBoolean("perform_updates", false))
        	  interval = Integer.parseInt(sharedPrefs.getString("updates_interval", "-1"));

    	  dNames = new ArrayList<String>();
          cNames = new ArrayList<String>();
          cUris = new ArrayList<String>();

    	  if (isServerReachable(uristr)) {
     		  uri = URI.create(uristr);
     		  client = new XMLRPCClient(uri);

     		  this.getConnectionsMethod();
    	  }
    	  else
    		  textView.setText("Server unreachable");

          final Handler uiHandler = new Handler();

          thread = new Thread(new Runnable() {
        	  private long lastTime;

              public void run(){
            	  while ( true ) {
            	    if ((interval > 0) && (domainList)
            	    		&& (System.currentTimeMillis() > lastTime + interval)) {
            		  this.refresh();
            		  lastTime = System.currentTimeMillis();
            	    }
            	  }
              }

              public void refresh() {
                  uiHandler.post(new Runnable() {
                      public void run(){
                  		  getDomainList();
                      }
                  });
              }
          });

          thread.start();

          listView.setOnItemClickListener(new OnItemClickListener() {
              public void onItemClick(AdapterView<?> parent, View view,
                  int position, long id) {
            	if (changingUri) {
              	    myUri = findMatch(cNames, ((TextView) view).getText().toString(), cUris);
              	    getDomainList();
            	}
            	else {
            		String name = ((TextView) view).getText().toString();

            		getDomainInformation(name);
            	}
              }
            });
        }

	@Override
	public void onDestroy() {
		try {
	        thread.stop();
		} catch (Exception e) {
			// Do nothing
		}

	    super.onDestroy();
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
	    if (keyCode == KeyEvent.KEYCODE_BACK) {
	    	if (phaseThree) {
	    		this.getDomainList();
	    		return true;
	    	}
	    	else
	    	if (!changingUri) {
          		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
           		String uristr = sharedPrefs.getString("address", "NULL");

          	    if (isServerReachable(uristr)) {
         		    uri = URI.create(uristr);
         		    client = new XMLRPCClient(uri);

         		    this.getConnectionsMethod();
        	    }
        	    else
        		    textView.setText("Server unreachable");

	    		return true;
	    	}
	    	else
	    	if (inAboutDlg) {
	    		this.getConnectionsMethod();
	    		return true;
	    	}
	    }

	    return super.onKeyUp(keyCode, event);
	}

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
        ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
        String listItemName = dNames.get(info.position);

  	    if (listItemName.contains(" ")) {
		    String[] tmp = listItemName.split(" ");
		    listItemName = tmp[0];
	    }

  	    menu.setHeaderTitle( "Domain actions for " + listItemName );
        String[] menuItems = getResources().getStringArray(R.array.domActions);
        for (int i = 0; i<menuItems.length; i++) {
          menu.add(Menu.NONE, i, i, menuItems[i]);
      }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
      AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
      //String[] menuItems = getResources().getStringArray(R.array.domActions);
      String domainName = dNames.get(info.position);
      String action = item.getTitle().toString();

	  if (domainName.contains(" ")) {
		  String[] tmp = domainName.split(" ");
	      domainName = tmp[0];
	  }

      if (action.equals("Start")) {
    	  if (this.isDomainRunning(domainName)) {
    		  Toast.makeText(getApplicationContext(), "Domain is already running", Toast.LENGTH_SHORT).show();
    		  return true;
    	  }

    	  setDomainActivity(domainName, true);
    	  this.getDomainList();
      }
      else
      if (action.equals("Stop")) {
    	  if (!this.isDomainRunning(domainName)) {
    		  Toast.makeText(getApplicationContext(), "Domain is not running yet", Toast.LENGTH_SHORT).show();
    		  return true;
          }

    	  setDomainActivity(domainName, false);
    	  this.getDomainList();
      }
      else
      if (action.equals("Get information")) {
    	  getDomainInformation(domainName);
      }
      return true;
    }

    private String findMatch(ArrayList<String> str, String match, ArrayList<String> subst) {
    	for (int i = 0; i < str.size(); i++) {
    		if (str.get(i).equals(match))
    			return subst.get(i);
    	}

    	return null;
    }

    private void refreshConnectionList() {
  		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
   		String uristr = sharedPrefs.getString("address", "NULL");
        if (sharedPrefs.getBoolean("perform_updates", false))
      	  interval = Integer.parseInt(sharedPrefs.getString("updates_interval", "-1"));

        try {
       		uri = URI.create(uristr);
       		client = new XMLRPCClient(uri);

       		this.getConnectionsMethod();
        } catch (Exception e) {
        	Toast.makeText(getApplicationContext(), "Error on creating URI",
        			Toast.LENGTH_LONG).show();
        }

   		changingUri = true;
   		inAboutDlg = false;
   		phaseThree = false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	menu.clear();
    	if (inAboutDlg)
    		return super.onPrepareOptionsMenu(menu);

    	if (phaseThree) {
            menu.add(Menu.NONE, 0, 0, "Start");
            menu.add(Menu.NONE, 1, 0, "Stop");
            menu.add(Menu.NONE, 2, 0, "Refresh");
    	}
    	else
    	if (changingUri) {
            menu.add(Menu.NONE, 0, 0, "Options");
            menu.add(Menu.NONE, 1, 0, "Refresh");
            menu.add(Menu.NONE, 2, 0, "About");
    	}

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
            	if (phaseThree) {
            		setDomainActivity(domName, true);
            		this.getDomainInformation(domName);
        		}
            	else
            		startActivity(new Intent(this, PrefsActivity.class));
                return true;
            case 1:
            	if (phaseThree) {
            		setDomainActivity(domName, false);
            		this.getDomainInformation(domName);
            	}
            	else
            		this.refreshConnectionList();
                return true;
            case 2:
            	if (phaseThree)
            		this.getDomainInformation(domName);
            	else {
            		String versionName = "?";

            		try {
						PackageInfo pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
						versionName = pinfo.versionName;
					} catch (NameNotFoundException e) {
						/* Should never happen */
						e.printStackTrace();
					}

            		textView.setText("VirtDroid v" + versionName + "\n\n" +
            				"Written by Michal Novotny <mignov@gmail.com>\n\n" +
            				"VirtDroid is an open-source tool to connect to php-virt-control " +
            				"XMLRPC for Android operating system. This tool has been designed " +
            				"for virtual machine management over the network and may be useful " +
            				"for the node management for IT managers in companies and/or " +
            				"hosting providers.\n\n" +
            				"Project is using thin Android XMLRPC client library which can be found at:\n" +
            				"http://code.google.com/p/android-xmlrpc\n\n" +
            				"VirtDroid source codes are available at:\n" +
            				"http://www.github.com/MigNov/virtDroid\n\n" +
            				"Both XMLRPC client and VirtDroid source codes are published under " +
            				"Apache Licence 2.0 (ASL-2.0) licence");
            		listView.setVisibility(View.INVISIBLE);
            		inAboutDlg = true;
            	}
                return true;
        }
        return false;
    }

	@SuppressWarnings("unchecked")
	private void getConnectionsMethod() {
		textView.setText("");
		domainList = false;
		inAboutDlg = false;
		listView.setVisibility(View.INVISIBLE);
		try {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
            String apikey = sharedPrefs.getString("apikey", "NULL");

            HashMap<String, String> cParams = new HashMap<String, String>();
            cParams.put("uri", "list");

            HashMap<String, String> dParams = new HashMap<String, String>();
            dParams.put("type", "list");

            HashMap<String, Object> params = new HashMap<String, Object>();
        	params.put("apikey", apikey);
        	params.put("connection", cParams);
        	params.put("data", dParams);

        	changingUri = true;

        	cNames.clear();
        	cUris.clear();

        	Map<String, Object> res = (Map<String, Object>) client.call("Information.get", params);
        	for (String key : res.keySet()) {
        	    Map<String, String> value = (Map<String, String>) res.get(key);

        	    String uri = value.get("uri");
        	    String name = value.get("name");

        	    cNames.add(name);
        	    cUris.add(uri);
        	}

        	listView.setAdapter(new ArrayAdapter<String>(this,
  	                android.R.layout.simple_list_item_1,
  	                cNames));

        	listView.setVisibility(View.VISIBLE);
		} catch (XMLRPCFault f) {
			textView.setText("XMLRPC returned error: " + f.getFaultString());
		} catch (XMLRPCException e) {
			textView.setText("XMLRPC connection error: " + e.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	private void getDomainList() {
    	changingUri = false;
    	phaseThree = false;
		textView.setText("Domain list: ");
		domainList = true;
		inAboutDlg = false;
		try {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
            String apikey = sharedPrefs.getString("apikey", "NULL");
            if (sharedPrefs.getBoolean("perform_updates", false))
            	  interval = Integer.parseInt(sharedPrefs.getString("updates_interval", "-1"));

            HashMap<String, String> cParams = new HashMap<String, String>();
            cParams.put("uri", myUri);

            HashMap<String, String> dParams = new HashMap<String, String>();
            dParams.put("type", "list");

            HashMap<String, Object> params = new HashMap<String, Object>();
        	params.put("apikey", apikey);
        	params.put("connection", cParams);
        	params.put("data", dParams);

        	dNames.clear();
			Map<String, Object> res = (Map<String, Object>) client.call("Domain.list_state", params);
        	for (String key : res.keySet()) {
        		dNames.add(res.get(key).toString());
        	}

        	listView.setAdapter(new ArrayAdapter<String>(this,
  	                android.R.layout.simple_list_item_1,
  	                dNames));
    		listView.setVisibility(View.VISIBLE);
		} catch (XMLRPCFault f) {
			textView.setText("XMLRPC returned error: " + f.getFaultString());
		} catch (XMLRPCException e) {
			textView.setText("XMLRPC connection error: " + e.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	private void getDomainInformation(String name) {
    	changingUri = false;
		inAboutDlg = false;
		textView.setText("Domain information:");
		listView.setVisibility(View.INVISIBLE);

		if (name.contains(" ")) {
		    String[] tmp = name.split(" ");
		    name = tmp[0];
		}

		domName = name;
		domainList = false;

		try {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
            String apikey = sharedPrefs.getString("apikey", "NULL");

            HashMap<String, String> cParams = new HashMap<String, String>();
            cParams.put("uri", myUri);

            HashMap<String, String> dParams = new HashMap<String, String>();
            dParams.put("name", name);

            HashMap<String, Object> params = new HashMap<String, Object>();
        	params.put("apikey", apikey);
        	params.put("connection", cParams);
        	params.put("data", dParams);

        	Map<String, Object> res = (Map<String, Object>) client.call("Domain.info", params);

        	String newText = "Domain name: " + name + "\nState: " + res.get("state") + "\nArchitecture: "
        			+ res.get("arch") + "\nvCPUs: " + res.get("nrVirtCpu") + "\nMemory: " +
        			(Integer.parseInt(res.get("memory").toString()) / 1024) + " MiB / " +
        			(Integer.parseInt(res.get("maxMem").toString()) / 1024) + " MiB\nFeatures: " +
        			res.get("features")+"\nClock offset: " + res.get("clock-offset") + "\n";

        	Map<String, Object> res2 = (Map<String, Object>) res.get("boot_devices");
        	newText += "First boot device: " + res2.get("0") + "\n";

        	res2 = (Map<String, Object>) res.get("multimedia");
        	newText += "\nMultimedia:\n";
        	Map<String, Object> res3 = (Map<String, Object>) res2.get("video");
        	newText += "\tVideo:\n\t\tType: " + res3.get("type") + "\n\t\tVideo RAM: " +
        			(Integer.parseInt(res3.get("vram").toString()) / 1024) + " MiB\n\t\tHeads: " + res3.get("heads")+"\n";
        	res3 = (Map<String, Object>) res2.get("graphics");
        	newText += "\tGraphics:\n\t\tType: " + res3.get("type") + "\n\t\tAutoport: " +
     			   res3.get("autoport") + "\n\t\tPort: " + res3.get("port")+"\n";
        	res3 = (Map<String, Object>) res2.get("input");
        	newText += "\tInput:\n\t\tType: " + res3.get("type") + "\n\t\tBus: " +
     			   res3.get("bus")+"\n";
        	res3 = (Map<String, Object>) res2.get("console");
        	newText += "\tConsole:\n\t\tType: " + res3.get("type") + "\n\t\tTarget port: " +
     			   res3.get("targetPort")+"\n\t\tTarget type: " + res3.get("targetType");

        	textView.setText(newText);
        	phaseThree = true;
		} catch (XMLRPCFault f) {
			textView.setText("XMLRPC returned error: " + f.getFaultString());
		} catch (XMLRPCException e) {
			textView.setText("XMLRPC connection error: " + e.getMessage());
		}
	}

	private String getDomainState(String name) {
		String ret = null;

		try {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
            String apikey = sharedPrefs.getString("apikey", "NULL");

            HashMap<String, String> cParams = new HashMap<String, String>();
            cParams.put("uri", myUri);

            HashMap<String, String> dParams = new HashMap<String, String>();
            dParams.put("name", name);

            HashMap<String, Object> params = new HashMap<String, Object>();
        	params.put("apikey", apikey);
        	params.put("connection", cParams);
        	params.put("data", dParams);

        	@SuppressWarnings("unchecked")
			Map<String, Object> res = (Map<String, Object>) client.call("Domain.info", params);
        	ret = res.get("state").toString();
		} catch (XMLRPCFault f) {
			textView.setText("XMLRPC returned error: " + f.getFaultString());
		} catch (XMLRPCException e) {
			textView.setText("XMLRPC connection error: " + e.getMessage());
		}

		return ret;
	}

	@SuppressWarnings("unchecked")
	private String setDomainActivity(String name, Boolean active) {
		String ret = null;
		String action = null;

		try {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
            String apikey = sharedPrefs.getString("apikey", "NULL");

            HashMap<String, String> cParams = new HashMap<String, String>();
            cParams.put("uri", myUri);

            HashMap<String, String> dParams = new HashMap<String, String>();
            dParams.put("name", name);

            HashMap<String, Object> params = new HashMap<String, Object>();
        	params.put("apikey", apikey);
        	params.put("connection", cParams);
        	params.put("data", dParams);

        	if (active)
        		action = "start";
        	else
        		action = "stop";

			Map<String, Object> res = (Map<String, Object>) client.call("Domain." + action, params);
        	Toast.makeText(getApplicationContext(), res.get("msg").toString(), Toast.LENGTH_SHORT).show();
		} catch (XMLRPCFault f) {
			textView.setText("XMLRPC returned error: " + f.getFaultString());
		} catch (XMLRPCException e) {
			textView.setText("XMLRPC connection error: " + e.getMessage());
		}

		return ret;
	}

	private Boolean isDomainRunning(String name) {
		return this.getDomainState(name).equals("running");
	}
}
