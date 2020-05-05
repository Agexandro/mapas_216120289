package com.alex.mapas;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap; //Variable del mapa
    private static final int LOCATION_REQUEST=500; //Codigo para permitir el permiso de ubicación
    ArrayList<LatLng> listPoints;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        //inicialización del array
        listPoints = new ArrayList<>();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.getUiSettings().setZoomControlsEnabled(true);
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST);
            return;
        }
        mMap.setMyLocationEnabled(true);
        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                //limpiar la lista si hay dos elementos
                if(listPoints.size() == 2){
                    listPoints.clear();
                    mMap.clear();
                }
                //Guardar la primer coordenada
                listPoints.add(latLng);
                //marcador de la primera coordenada
                MarkerOptions markerOptions = new MarkerOptions();
                markerOptions.position(latLng);

                if (listPoints.size()==1){
                    //mostrar el primer marcador en el mapa
                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                }else{
                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
                }
                mMap.addMarker(markerOptions);
                // mMap.setMapType(1);

                //Direcciones para construir la ruta
                if(listPoints.size()==2){
                    String url = getRequestUrl(listPoints.get(0),listPoints.get(1));
                    TaskRequestDirections taskRequestDirections = new TaskRequestDirections();
                    taskRequestDirections.execute(url);
                }
            }
        });
    }

    private String getRequestUrl(LatLng origen, LatLng destino) {
        //Valor del origen
        String orig = "origin="+origen.latitude+","+origen.longitude;
        //Valor del destino
        String dest = "destination="+destino.latitude+","+destino.longitude;
        //sensor
        String sensor = "sensor=false";
        //encontrar modo de direcciones
        String mode = "mode=driving";
        //Crear el parametro completo
        String param = orig+"&"+dest+"&"+sensor+"&"+mode;
        //formato de salida
        String salida = "json";
        //crear url request
        String url = "https://maps.googleapis.com/maps/api/directions/"+salida+"?"+param+"&key="+getString(R.string.google_maps_key);
        Log.d("url",url);
        return url;
    }

    private String requestDirection(String reqURL) throws IOException {
        String responseString = "";
        InputStream inputStream = null;
        HttpURLConnection httpURLConnection = null;
        try{
            URL url = new URL(reqURL);
            httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.connect();

            //Obtener el resultado
            inputStream = httpURLConnection.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            StringBuffer stringBuffer = new StringBuffer();
            String line = "";
            while((line = bufferedReader.readLine())!=null){
                stringBuffer.append(line);
            }
            responseString = stringBuffer.toString();
            bufferedReader.close();
            inputStreamReader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if(inputStream != null){
                inputStream.close();
            }
            httpURLConnection.disconnect();
        }
        return  responseString;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case LOCATION_REQUEST:
                if(grantResults.length >=0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    mMap.setMyLocationEnabled(true);
                }
                break;
        }
    }


    public  class TaskRequestDirections extends AsyncTask<String,Void, String>{

        @Override
        protected String doInBackground(String... strings) {
            String response ="";
            try {
                response =  requestDirection(strings[0]);
            }catch (Exception e){
                e.printStackTrace();
            }
            return response;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            //Parsear Json
            TaskParser taskParser = new TaskParser();
            taskParser.execute(s);
        }
    }

    public  class TaskParser extends AsyncTask<String, Void,  List<List<HashMap<String, String>>> >{

        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... strings) {
            JSONObject jsonObject = null;
            List< List<HashMap<String, String>>> rutas = null;

            try {
                jsonObject = new JSONObject(strings[0]);
                DirectionsParser directionsParser = new DirectionsParser();
                rutas = directionsParser.parse(jsonObject);
            }catch (Exception e){
                e.printStackTrace();
            }
            return rutas;
        }

        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> lists) {
            //obtener lista de rutas y mostrarlas al mapa
            ArrayList points = null;
            PolylineOptions polylineOptions = null;

            for(List<HashMap<String, String>> path : lists){
                points = new ArrayList();
                polylineOptions = new PolylineOptions();
                for(HashMap<String, String> point: path){
                    double latitud = Double.parseDouble(point.get("lat"));
                    double longitud = Double.parseDouble(point.get("lon "));
                    points.add(new LatLng(latitud,longitud));
                }
                polylineOptions.addAll(points);
                polylineOptions.width(15);
                polylineOptions.color(Color.RED);
                polylineOptions.geodesic(true);

                if(polylineOptions !=null){
                    mMap.addPolyline(polylineOptions);
                }else{
                    Toast.makeText(getApplicationContext(),"Dirección no encontrada",Toast.LENGTH_LONG).show();

                }
            }
        }
    }
}
