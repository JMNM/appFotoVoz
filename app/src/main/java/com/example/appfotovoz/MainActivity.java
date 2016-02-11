/*
    Copyright (C) 2016  José Miguel Navarro Moreno and José Antonio Larrubia García

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.example.appfotovoz;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.speech.RecognizerIntent;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;
import android.os.Build;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;

/**
 *  Aplicación de una brujula que le indica una dirección por voz y la muestra.
 *  @autor José Miguel Navarro Moreno
 *  @autor José Antonio Larrubia García
 *  @version 9.2.2016
 */
public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private ImageView imgBrujula;
    private ImageView flecha;
    private TextView txtAngle;
    private TextView textoRec;
    // guarda el angulo (grado) actual del compass
    private float currentDegree = 0f;

    // El sensor manager del dispositivo
    private SensorManager mSensorManager;
    // Los dos sensores que son necesarios
    private Sensor accelerometer;
    private Sensor magnetometer;

    // Los angulos del movimiento respecto al norte
    float degree;
    // Guarda el valor del azimut
    float azimut;
    // Guarda los valores que cambián con las variaciones del sensor TYPE_ACCELEROMETER
    float[] mGravity;
    // Guarda los valores que cambián con las variaciones del sensor TYPE_MAGNETIC_FIELD
    float[] mGeomagnetic;

    //--------------------------------------------------------------------------------------
    // Referencia:https://github.com/zoraidacallejas/sandra/tree/master/Apps/ASRWithIntent
    // Default values for the language model and maximum number of recognition results
    // They are shown in the GUI when the app starts, and they are used when the user selection is not valid
    private final static int DEFAULT_NUMBER_RESULTS = 10;
    private final static String DEFAULT_LANG_MODEL = RecognizerIntent.LANGUAGE_MODEL_FREE_FORM;

    private int numberRecoResults = DEFAULT_NUMBER_RESULTS;
    private String languageModel = DEFAULT_LANG_MODEL;

    private static final String LOGTAG = "ASRBEGIN";
    private static int ASR_CODE = 123;

    //--------------------------------------------------------------------------------------

    private String Direccion=null;
    private int margen_error=30;
    float angulo_flecha;
    float angulo_ant=0f;

    /**
     * Crea la interfaz de la aplicación
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //boton de la esquina inferior derecha que inicia el detector de voz cuando lo pulsas
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //----------------------------------------------------------------------------------------
                //Referencia:https://github.com/zoraidacallejas/sandra/tree/master/Apps/ASRWithIntent
                //Speech recognition does not currently work on simulated devices,
                //it the user is attempting to run the app in a simulated device
                //they will get a Toast
                if("generic".equals(Build.BRAND.toLowerCase())){
                    Toast toast = Toast.makeText(getApplicationContext(),"ASR is not supported on virtual devices", Toast.LENGTH_SHORT);
                    toast.show();
                    Log.d(LOGTAG, "ASR attempt on virtual device");
                }
                else{
                    numberRecoResults = 10;
                    languageModel = DEFAULT_LANG_MODEL; //Read speech recognition parameters from GUI
                    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    // Specify language model
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, languageModel);
                    // Specify how many results to receive
                    intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, numberRecoResults);
                    // Start listening
                    startActivityForResult(intent, ASR_CODE);
                }
                //------------------------------------------------------------------------------------------------
            }
        });

        //imagen que estara orientada hacia el norte
        imgBrujula = (ImageView) findViewById(R.id.imageBrujula);
        //imagen que indica la dirección captada por voz
        flecha=(ImageView) findViewById(R.id.imageFlecha);
        txtAngle= (TextView) findViewById(R.id.textView);

        // Se inicializa los sensores del dispositivo
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        mGravity = null;
        mGeomagnetic = null;
    }

    /**
     * Metodo para finalizar la aplicación
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

   @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /**
     * Para reanudar la aplicación despues de pausarla.
     */
    @Override
    protected void onResume() {
        super.onResume();

        // Se registra un listener para los sensores del accelerometer y el magnetometer
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    /**
     * Para pausar la aplicaión
     */
    @Override
    protected void onPause() {
        super.onPause();
        // Se detiene el listener para no malgastar la bateria
        mSensorManager.unregisterListener(this);
    }

    /**
     * Cuando cambia el estado del sensor se realizan las acciones necesarias en la aplicción.
     * @param event Evento producido por el cambio del sensor
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        //-------------------------------------------------------------------------
        //Referencia: http://agamboadev.esy.es/como-crear-un-brujula-en-android/
        // Se comprueba que tipo de sensor está activo en cada momento
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                mGravity = event.values;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                mGeomagnetic = event.values;
                break;
        }
        if ((mGravity != null) && (mGeomagnetic != null)) {
            float RotationMatrix[] = new float[16];
            boolean success = SensorManager.getRotationMatrix(RotationMatrix,null, mGravity, mGeomagnetic);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(RotationMatrix, orientation);
                azimut = orientation[0] * (180 / (float) Math.PI);
            }
        }
        degree = azimut;
        //se muestra el angulo del norte respecto a la dirección del dispositivo.
        txtAngle.setText("Ángulo: " + Float.toString(degree) + " grados");
        // se crea la animacion de la rottacion (se revierte el giro en grados, negativo)
        RotateAnimation ra = new RotateAnimation(
                currentDegree,
                degree,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f);
        // el tiempo durante el cual la animación se llevará a cabo
        ra.setDuration(1000);
        // establecer la animación después del final de la estado de reserva
        ra.setFillAfter(true);
        // Inicio de la animacion
        imgBrujula.startAnimation(ra);
        currentDegree = -degree;
        //-----------------------------------------------------------------

        //Si se le ha pasado una dirección por voz
        if(Direccion!=null){
            if(Direccion.equals("sur")){
                if(degree>0)
                    angulo_flecha=degree-180;
                else
                    angulo_flecha=degree+180;
            }else if(Direccion.equals("norte")){
                angulo_flecha=degree;
            }else if(Direccion.equals("este")){
                if(degree-90<-180)
                    angulo_flecha=degree-90+360;
                else angulo_flecha=degree-90;
            }else if(Direccion.equals("oeste")){
                if(degree+90>180)
                    angulo_flecha=degree+90-360;
                else angulo_flecha=degree+90;
            }

            RotateAnimation ra2 = new RotateAnimation(
                    angulo_ant,
                    angulo_flecha,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF,
                    0.5f);
            // el tiempo durante el cual la animación se llevará a cabo
            ra2.setDuration(1000);
            // establecer la animación después del final de la estado de reserva
            ra2.setFillAfter(true);
            // Inicio de la animacion
            flecha.startAnimation(ra2);


            angulo_ant=-angulo_flecha;

            //Se el dipositivo apunta hacia la dirección especificada se cambia el color de la flecha
            if(angulo_flecha<margen_error && angulo_flecha>-margen_error){
                flecha.setImageResource(R.drawable.flechag);
            }else flecha.setImageResource(R.drawable.flechar);

        }
    }

    /**
     *  Se recoge los datos del evento de captación de voz
     */
    @SuppressLint("InlinedApi")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //Referencia:https://github.com/zoraidacallejas/sandra/tree/master/Apps/ASRWithIntent
        if (requestCode == ASR_CODE)  {
            if (resultCode == RESULT_OK)  {
                if(data!=null) {
                    //Retrieves the N-best list and the confidences from the ASR result
                    ArrayList<String> nBestList = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

                    //Para obtener la direccion y el margen de error
                    obtenerDireccion(nBestList);

                    textoRec= (TextView) findViewById(R.id.textRec);
                    textoRec.setText("Dirección: "+Direccion + " " + margen_error);

                }
            }
            else {
                //Reports error in recognition error in log
                Log.e(LOGTAG, "Recognition was not successful");
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    /**
     * Método para obtener la dirección y el margen de error de los resultados de la captacion de voz.
     * @param a Lista con les resultados del reconocedor de voz
     */
    private void obtenerDireccion(ArrayList<String> a){
        ArrayList<String> puntos_car=new ArrayList<String>();
        puntos_car.add("norte");
        puntos_car.add("sur");
        puntos_car.add("este");
        puntos_car.add("oeste");
        boolean coincide=false;
        Direccion=null;

        // se comprueba de la lista de string de la detección de voz
        for(int i=0;i<puntos_car.size() && !coincide;i++){
            for(int j=0;j<a.size()&&!coincide;j++){
                if(a.get(j).length()>=puntos_car.get(i).length()) {
                    //Si coincide con un punto cardinal se guarda en dirección
                    if (puntos_car.get(i).equals((a.get(j).substring(0, puntos_car.get(i).length())).toLowerCase())) {
                        coincide = true;
                        Direccion = puntos_car.get(i);
                    }
                }
            }
        }
        String numero;
        boolean es_num=false;
        //se obtiene el margen de error
        if(Direccion!=null) {
            //limpiamos el string obtenido por el detector de voz para que solo contenga el número eliminado todas las letras
            //Referencia: http://puntocomnoesunlenguaje.blogspot.com.es/2013/07/ejemplos-expresiones-regulares-java-split.html
            Pattern pat = Pattern.compile("[a-zA-Záéíóú]*(\\s)*[a-zA-Záéíóú]*");
            Matcher mat;
            for (int i = 0; i < a.size() && !es_num; i++) {
                mat= pat.matcher(a.get(i));
                //quitamos las letras del string
                numero=mat.replaceAll("");
                //si en el string se obtiene un número ese será el margen de error.
                if(numero.length()>0){
                    margen_error=Integer.parseInt(numero);
                    es_num=true;
                }
            }
        }
    }
}
