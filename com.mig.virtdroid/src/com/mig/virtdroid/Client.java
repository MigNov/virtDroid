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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
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

class phpVirtControlXMLRPC extends AsyncTask<HashMap<String, Object>, Void, Map<String, Object>> {

	Map<String, Object> res = null;
	private XMLRPCClient client = null;
	private ListView listView;
	private TextView textView;
	private String type;
	private Context context;
	private Client appClass = null;
	private ArrayList<String> dNames = new ArrayList<String>();
	private ArrayList<String> cNames = new ArrayList<String>();
	private ArrayList<String> cUris = new ArrayList<String>();
	private String message;

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

	private void setError(final String str) {
		this.appClass.setProcessingState(false, null);
        this.appClass.runOnUiThread(new Runnable() {
            public void run() {
            	textView.setText(str);
            }
        });
	}

	protected Map<String, Object> doInBackground(HashMap<String,Object>... params) {
		HashMap<String, Object> param = params[0];
		this.type = (String) param.get("page");
		this.textView = (TextView) param.get("textView");
		this.listView = (ListView) param.get("listView");
		this.dNames = (ArrayList<String>) param.get("dNames");
		this.cNames = (ArrayList<String>) param.get("cNames");
		this.cUris = (ArrayList<String>) param.get("cUris");
		this.context = (Context) param.get("context");
		this.appClass = (Client) param.get("appClass");
		this.message = (String) param.get("message");
		String uristr = (String) param.get("address");
		String method = (String) param.get("method");

		param.remove("textView");
		param.remove("listView");
		param.remove("dNames");
		param.remove("cNames");
		param.remove("cUris");
		param.remove("context");
		param.remove("cNames");
		param.remove("dNames");
		param.remove("cUris");
		param.remove("appClass");
		param.remove("message");

		try {
			if (this.isServerReachable(uristr)) {
				URI uri = URI.create(uristr);
	     	    client = new XMLRPCClient(uri);

		    	@SuppressWarnings("unchecked")
				Map<String, Object> res = (Map<String, Object>) client.call(method, param);
				return res;
	    	  }
	    	  else
	    		this.setError("Server unreachable");
	    } catch (XMLRPCFault f) {
	    	this.setError("XMLRPC returned error: " + f.getFaultString());
	    } catch (XMLRPCException e) {
	    	this.setError("XMLRPC connection error: " + e.getMessage());
	    }

	    return null;
	}

	@SuppressWarnings("unchecked")
	public void onPostExecute(Map<String, Object> res) {
	    this.appClass.setProcessingState(false, this.message);

	    if (res == null)
			return;

	    cNames = this.cNames;
	    dNames = this.dNames;
	    cUris  = this.cUris;

	    if (cNames == null)
	    	cNames = new ArrayList<String>();
	    if (cUris == null)
	    	cUris = new ArrayList<String>();
	    if (dNames == null)
	    	dNames = new ArrayList<String>();

	    if (this.type.equals("connections")) {
	    	for (String key : res.keySet()) {
				Map<String, String> value = (Map<String, String>) res.get(key);

	    	    String uri = value.get("uri");
	    	    String name = value.get("name");

	    	    cNames.add(name);
	    	    cUris.add(uri);
	    	}

    	    this.appClass.setConnections(cNames, cUris);
	    	this.listView.setAdapter(new ArrayAdapter<String>(this.context,
	                android.R.layout.simple_list_item_1,
	                cNames));

	    	this.listView.setVisibility(View.VISIBLE);
		}
		else
		if (this.type.equals("domains")) {
   	    	for (String key : res.keySet()) {
   	    		dNames.add(res.get(key).toString());
   	    	}

    	    this.appClass.setDomains(dNames);

   	    	listView.setAdapter(new ArrayAdapter<String>(this.context,
   	    			android.R.layout.simple_list_item_1,
   	    			dNames));
   	    	listView.setVisibility(View.VISIBLE);
		}
		else
		if (this.type.equals("domain-info")) {
        	String newText = "Domain information:\nState: " + res.get("state") + "\nArchitecture: "
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
		}
		else
		if (this.type.equals("domain-state")) {
   	    	Toast.makeText(this.context, res.get("state").toString(), Toast.LENGTH_SHORT).show();
		}
		else
     	if (this.type.equals("domain-activity")) {
 			Toast.makeText(this.context, res.get("msg").toString(), Toast.LENGTH_SHORT).show();
     	}
	}
 }

public class Client extends Activity {
	private TextView textView;
	private ListView listView;
	private ArrayList<String> dNames;
	private ArrayList<String> cNames;
	private ArrayList<String> cUris;
	private String myUri = null;
	private Boolean changingUri = true;
	private Boolean phaseThree = false;
	private Boolean inDomainList = false;
	private Boolean inAboutDlg = false;
	private String domName = null;
	private boolean isProcessingState;
	private int interval = -1;
	private Thread thread = null;

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
          if (sharedPrefs.getBoolean("perform_updates", false))
        	  interval = Integer.parseInt(sharedPrefs.getString("updates_interval", "-1"));

    	  dNames = new ArrayList<String>();
          cNames = new ArrayList<String>();
          cUris = new ArrayList<String>();

          this.getConnectionsMethod();

          final Handler uiHandler = new Handler();
          thread = new Thread(new Runnable() {
        	  private long lastTime;
        	  private boolean isStopped = false;

              public void run(){
            	  while ( true ) {
            	    if ((!isStopped) && ((interval > 0) && (inDomainList)
            	    		&& (System.currentTimeMillis() > lastTime + interval))) {
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
			thread.interrupt();
		} catch (Exception e) {
			// Make Java happy :-)
		}

	    super.onDestroy();
	}

	public void setConnections(ArrayList<String> aNames, ArrayList<String> aUris) {
		cUris = aUris;
		cNames = aNames;
	}

	public void setDomains(ArrayList<String> aNames) {
		dNames = aNames;
	}

	public void setTextView(String str) {
    	textView.setText(str);
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
       		    this.getConnectionsMethod();
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
        if ((info == null) || (info.position > dNames.size() - 1))
        	return;

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
      if ((info == null) || (info.position > dNames.size() - 1))
      	return false;
      String domainName = dNames.get(info.position);
      String action = item.getTitle().toString();
      String state = null;

	  if (domainName.contains(" ")) {
		  String[] tmp = domainName.split(" ");
	      domainName = tmp[0];
	      state = tmp[1];
	  }

      if (action.equals("Start")) {
    	  if (state.equals("(running)")) {
    		  Toast.makeText(getApplicationContext(), "Domain is already running", Toast.LENGTH_SHORT).show();
    		  return true;
    	  }

  		  inDomainList = false;
  		  phaseThree = true;
    	  setDomainActivity(domainName, true, true);
      }
      else
      if (action.equals("Stop")) {
    	  if (!state.equals("(running)")) {
    		  Toast.makeText(getApplicationContext(), "Domain is not running yet", Toast.LENGTH_SHORT).show();
    		  return true;
          }

  		  inDomainList = false;
  		  phaseThree = true;
    	  setDomainActivity(domainName, false, true);
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
       	this.getConnectionsMethod();

   		changingUri = true;
   		inAboutDlg = false;
   		phaseThree = false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	menu.clear();
    	if (inAboutDlg)
    		return super.onPrepareOptionsMenu(menu);

    	if (changingUri) {
            menu.add(Menu.NONE, 0, 0, "Options");
            menu.add(Menu.NONE, 1, 0, "Refresh");
            menu.add(Menu.NONE, 2, 0, "About");
    	}
    	else
    	if (inDomainList)
    		menu.add(Menu.NONE, 0, 0, "Refresh");
    	else
    	if (phaseThree) {
            menu.add(Menu.NONE, 0, 0, "Start");
            menu.add(Menu.NONE, 1, 0, "Stop");
            menu.add(Menu.NONE, 2, 0, "Refresh");
    	}

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
            	if (phaseThree) {
            		setDomainActivity(domName, true, false);
            		this.getDomainInformation(domName);
        		}
            	else
            	if (inDomainList)
            		getDomainList();
            	else
            		startActivity(new Intent(this, PrefsActivity.class));
                return true;
            case 1:
            	if (phaseThree) {
            		setDomainActivity(domName, false, false);
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
		if (this.isProcessingState)
			return;

		inAboutDlg = false;
		phaseThree = false;
		inDomainList = false;
		changingUri = true;
		listView.setVisibility(View.INVISIBLE);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        String address = sharedPrefs.getString("address", "NULL");
        String apikey = sharedPrefs.getString("apikey", "NULL");

        HashMap<String, String> cParams = new HashMap<String, String>();
        cParams.put("uri", "list");

        HashMap<String, String> dParams = new HashMap<String, String>();
        dParams.put("type", "list");

        HashMap<String, Object> params = new HashMap<String, Object>();
        params.put("address", address);
    	params.put("apikey", apikey);
    	params.put("page", "connections");
    	params.put("textView", textView);
    	params.put("listView", listView);
    	params.put("appClass", this);
    	params.put("message", "Connection list:");
    	params.put("context", getApplicationContext());
        params.put("method", "Information.get");
    	params.put("connection", cParams);
    	params.put("data", dParams);

    	changingUri = true;

    	cNames.clear();
    	cUris.clear();

   	    phpVirtControlXMLRPC rpc = new phpVirtControlXMLRPC();
   	    rpc.execute(params);

   	    this.setProcessingState(true, null);
}

	@SuppressWarnings("unchecked")
	private void getDomainList() {
		inDomainList = true;
		changingUri = false;
    	phaseThree = false;
		inAboutDlg = false;

		if (this.isProcessingState)
			return;

		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        String apikey = sharedPrefs.getString("apikey", "NULL");
        String address = sharedPrefs.getString("address", "NULL");

		if (sharedPrefs.getBoolean("perform_updates", false))
        	  interval = Integer.parseInt(sharedPrefs.getString("updates_interval", "-1"));

        HashMap<String, String> cParams = new HashMap<String, String>();
        cParams.put("uri", myUri);

        HashMap<String, String> dParams = new HashMap<String, String>();
        dParams.put("type", "list");

        HashMap<String, Object> params = new HashMap<String, Object>();
        params.put("address", address);
        params.put("method", "Domain.list_state");
    	params.put("apikey", apikey);
    	params.put("page", "domains");
    	params.put("textView", textView);
    	params.put("listView", listView);
    	params.put("message", "Domain list:");
    	params.put("context", getApplicationContext());
    	params.put("appClass", this);
    	params.put("connection", cParams);
    	params.put("data", dParams);

    	dNames.clear();

   	    phpVirtControlXMLRPC rpc = new phpVirtControlXMLRPC();
   	    rpc.execute(params);

   	    this.setProcessingState(true, null);
	}

	@SuppressWarnings("unchecked")
	private void getDomainInformation(String name) {
		inDomainList = false;
		changingUri = false;
		inAboutDlg = false;
   	    phaseThree = true;

   	    if (this.isProcessingState)
			return;

		textView.setText("Domain information:");
		listView.setVisibility(View.INVISIBLE);

		if (name.contains(" ")) {
		    String[] tmp = name.split(" ");
		    name = tmp[0];
		}

		domName = name;
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        String apikey = sharedPrefs.getString("apikey", "NULL");
        String address = sharedPrefs.getString("address", "NULL");

        HashMap<String, String> cParams = new HashMap<String, String>();
        cParams.put("uri", myUri);

        HashMap<String, String> dParams = new HashMap<String, String>();
        dParams.put("name", name);

        HashMap<String, Object> params = new HashMap<String, Object>();
        params.put("address", address);
        params.put("method", "Domain.info");
    	params.put("apikey", apikey);
    	params.put("page", "domain-info");
    	params.put("textView", textView);
    	params.put("listView", listView);
    	params.put("appClass", this);
    	params.put("message", "Domain information for " + name + ":");
    	params.put("context", getApplicationContext());
    	params.put("connection", cParams);
    	params.put("data", dParams);

   	    phpVirtControlXMLRPC rpc = new phpVirtControlXMLRPC();
   	    rpc.execute(params);

   	    this.setProcessingState(true, null);
	}

	void setProcessingState(boolean b, String str) {
		this.isProcessingState = b;
		if (b)
			this.textView.setText("Getting data ...");
		else
		if (str != null) {
			if (str.indexOf("@refresh=") > -1) {
				String name = str.split("=")[1];
				this.getDomainInformation(name);
			}
			else
			if (str.indexOf("@refreshDomainList") > -1) {
				this.getDomainList();
			}
			else
				this.textView.setText(str);
		}
	}

	@SuppressWarnings("unchecked")
	private void setDomainActivity(String name, Boolean active, Boolean toDomainList) {
		if (this.isProcessingState)
			return;

		String action = null;

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        String apikey = sharedPrefs.getString("apikey", "NULL");
        String address = sharedPrefs.getString("address", "NULL");

        HashMap<String, String> cParams = new HashMap<String, String>();
        cParams.put("uri", myUri);

        HashMap<String, String> dParams = new HashMap<String, String>();
        dParams.put("name", name);

        HashMap<String, Object> params = new HashMap<String, Object>();
        params.put("address", address);
    	params.put("apikey", apikey);
    	params.put("page", "domain-activity");
    	params.put("textView", textView);
    	params.put("listView", listView);
    	if (toDomainList)
    		params.put("message", "@refreshDomainList");
    	else
    		params.put("message", "@refresh="+name);
    	params.put("context", getApplicationContext());
    	params.put("appClass", this);
    	params.put("connection", cParams);
    	params.put("data", dParams);

    	if (active)
    		action = "start";
    	else
    		action = "stop";

        params.put("method", "Domain." + action);

   	    phpVirtControlXMLRPC rpc = new phpVirtControlXMLRPC();
   	    rpc.execute(params);

   	    this.setProcessingState(true, null);
	}
}
