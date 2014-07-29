package nl.tomsanders.lightswitch;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.util.Log;

public class HueEndpoint {
	public class HueLight {
		private String id;
		private JSONObject object;
		
		public HueLight(String id, JSONObject object) {
			this.id = id;
			this.object = object;
		}

		public void setOn(boolean b) {
			JSONObject params = new JSONObject();
			try {
				params.put("on", b);
				HueResponse.put(HueEndpoint.this, "/lights/" + id + "/state", params);
				
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
		}
		
		@Override
		public String toString() {
			return this.id;
		}
	}

	private String id, ip, mac, name;
	private String user;
	
	private HueEndpoint(String id, String ip, String mac, String name) {
		this.id = id;
		this.ip = ip;
		this.mac = mac;
		this.name = name;
	}
	
	public static HueEndpoint getDefaultEndpoint() {
		List<HueEndpoint> endpoints = HueEndpoint.getEndpoints();
		if (!endpoints.isEmpty())
			return endpoints.get(0);
		return null;
	}
	
	public static List<HueEndpoint> getEndpoints() {
		List<HueEndpoint> endpoints = new ArrayList<HueEndpoint>();
		
        try {
        	HttpClient client = new DefaultHttpClient();
			HttpResponse response = client.execute(new HttpGet("https://www.meethue.com/api/nupnp"));
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					response.getEntity().getContent(), "UTF-8"));
			StringBuilder contentBuilder = new StringBuilder();
			for (String line = null; (line = reader.readLine()) != null; ) {
				contentBuilder.append(line).append("\n");
			}
			
			String responseContent = contentBuilder.toString();
			JSONArray bridgeArray = new JSONArray(responseContent);
			for (int i = 0; i < bridgeArray.length(); i++) {
				JSONObject bridgeObject = bridgeArray.getJSONObject(i);
				endpoints.add(new HueEndpoint(
						bridgeObject.getString("id"),
						bridgeObject.getString("internalipaddress"),
						bridgeObject.getString("macaddress"),
						bridgeObject.getString("name")));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
        
        return endpoints;
	}
	
	public void setUser(String user) {
		this.user = user;
	}
	
	public boolean isAuthorized() {
		return HueResponse.get(this).getError() == HueResponse.NO_ERROR;
	}
	
	public void authorize() {
		String username = this.user;
		if (!this.isAuthorized()) {
			try {
				Log.v("nl.tomsanders.lightswitch", "Not authorized");
				this.user = "";
				
				JSONObject params = new JSONObject();
				params.put("devicetype", "android");
				params.put("username", username);
				
				HueResponse response;
				do {
					Thread.sleep(1000);
					response = HueResponse.post(this, "", params);
					Log.v("nl.tomsanders.lightswitch", "Auth status: " + 
							response.getErrorMessage() + " (" + response.getError() + ")");
				} while (response.getError() == 101);
				
				this.user = user;
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}
	}
	
	public List<HueLight> getLights() {
		List<HueLight> lights = new ArrayList<HueLight>();
		
		HueResponse response = HueResponse.get(this, "/lights");
		if (response.getError() != HueResponse.NO_ERROR) {
			Log.e("nl.tomsanders.lightswitch", "Error: " + response.getErrorMessage());
			return lights;
		}
		
		Iterator<?> keys = response.getContent().keys();
		while (keys.hasNext()) {
			String key = (String)keys.next();
			try {
				lights.add(new HueLight(key, (JSONObject)response.getContent().get(key)));
			} catch (JSONException e) { }
		}
		
		return lights;
	}
	
	@Override
	public String toString() {
		return this.name;
	}
	
	private static class HueResponse {
		private String raw;
		
		private static final int NO_ERROR = 0;
		private int errorType;
		private String errorMessage;
		private JSONObject content;
		
		public HueResponse(String raw) {
			this.raw = raw;
			
			try {
				Object json = new JSONTokener(raw).nextValue();
				if (json instanceof JSONArray) {
					JSONArray messages = (JSONArray)json;
					for (int i = 0; i < messages.length(); i++) {
						JSONObject message = messages.getJSONObject(i);
						if (message.has("error")) {
							JSONObject error = message.getJSONObject("error");
							
							this.errorType = error.getInt("type");
							this.errorMessage = error.getString("description");
						}
					}
				} else if (json instanceof JSONObject) {
					this.errorType = 0;
					this.errorMessage = "";
					
					this.content = new JSONObject(raw);
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		
		public int getError() {
			return this.errorType;
		}
		
		public String getErrorMessage() {
			return this.errorMessage;
		}
		
		public JSONObject getContent() {
			return this.content;
		}
		
		public static HueResponse get(HueEndpoint endpoint) {
			return HueResponse.get(endpoint, "");
		}
		
		public static HueResponse get(HueEndpoint endpoint, String path) {
			return HueResponse.execute(new HttpGet(getUri(endpoint, path)));
		}
		
		public static HueResponse post(HueEndpoint endpoint, String path, JSONObject body) {
			try {
				HttpPost request = new HttpPost(getUri(endpoint, path));
				request.setEntity(new ByteArrayEntity(body.toString().getBytes("UTF-8")));
				return HueResponse.execute(request);
				
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}
		
		public static HueResponse put(HueEndpoint endpoint, String path, JSONObject body) {
			try {
				HttpPut request = new HttpPut(getUri(endpoint, path));
				request.setEntity(new ByteArrayEntity(body.toString().getBytes("UTF-8")));
				return HueResponse.execute(request);
				
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}
		
		private static HueResponse execute(HttpUriRequest request) {
			try {
				HttpClient client = new DefaultHttpClient();
				HttpResponse response = client.execute(request);
				
				BufferedReader reader = new BufferedReader(new InputStreamReader(
						response.getEntity().getContent(), "UTF-8"));
				StringBuilder contentBuilder = new StringBuilder();
				for (String line = null; (line = reader.readLine()) != null; ) {
					contentBuilder.append(line).append("\n");
				}
				
				String responseContent = contentBuilder.toString();
				return new HueResponse(responseContent);
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}
		
		private static URI getUri(HueEndpoint endpoint, String path) {
			try {
				return new URI("http://" + endpoint.ip + "/api/" + endpoint.user + path);
			} catch (URISyntaxException ex) {
				throw new RuntimeException(ex);
			}
		}
	}
}
