package com.example.appfotovoz;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.content.Intent;
import android.widget.EditText;
import android.util.Log;
import android.widget.Toast;
import android.os.Build;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.Locale;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;



public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private ImageView imgBrujula;
    private ImageView flecha;
    private TextView txtAngle;
    private TextView textoRec;
    // guarda el angulo (grado) actual del compass
    private float currentDegree = 0f;

    // El sensor manager del dispositivo
    private SensorManager mSensorManager;
    // Los dos sensores que son necesarios porque TYPE_ORINETATION esta deprecated
    private Sensor accelerometer;
    private Sensor magnetometer;

    // Los angulos del movimiento de la flecha que señala al norte
    float degree;
    // Guarda el valor del azimut
    float azimut;
    // Guarda los valores que cambián con las variaciones del sensor TYPE_ACCELEROMETER
    float[] mGravity;
    // Guarda los valores que cambián con las variaciones del sensor TYPE_MAGNETIC_FIELD
    float[] mGeomagnetic;

    //-------------------------------------------------------------
    // Default values for the language model and maximum number of recognition results
    // They are shown in the GUI when the app starts, and they are used when the user selection is not valid
    private final static int DEFAULT_NUMBER_RESULTS = 10;
    private final static String DEFAULT_LANG_MODEL = RecognizerIntent.LANGUAGE_MODEL_FREE_FORM;


    private int numberRecoResults = DEFAULT_NUMBER_RESULTS;
    private String languageModel = DEFAULT_LANG_MODEL;

    private static final String LOGTAG = "ASRBEGIN";
    private static int ASR_CODE = 123;

    private TextToSpeech tts = null;

    //------------------------------------------------------------

    private String Direccion=null;
    private int margen_error=30;
    float angulo_flecha;
    float angulo_ant=0f;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
                    listen(); 				//Set up the recognizer with the parameters and start listening
                }
            }
        });

        imgBrujula = (ImageView) findViewById(R.id.imageBrujula);
        flecha=(ImageView) findViewById(R.id.imageFlecha);
        txtAngle= (TextView) findViewById(R.id.textView);

        // Se inicializa los sensores del dispositivo android
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        mGravity = null;
        mGeomagnetic = null;
    }

    /**
     * Initializes the speech recognizer and starts listening to the user input
     */
    private void listen()  {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        // Specify language model
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, languageModel);

        // Specify how many results to receive
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, numberRecoResults);

        // Start listening
        startActivityForResult(intent, ASR_CODE);
    }



    /**
     * Shuts down the TTS when finished
     */
    @Override
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

   @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
       getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Se registra un listener para los sensores del accelerometer y el             magnetometer
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Se detiene el listener para no malgastar la bateria
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
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
        txtAngle.setText("Angle: " + Float.toString(degree) + " degrees");
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

            if(angulo_flecha<margen_error && angulo_flecha>-margen_error){
                flecha.setImageResource(R.drawable.flechag);
            }else flecha.setImageResource(R.drawable.flechar);

        }
    }

    /**
     *  Shows the formatted best of N best recognition results (N-best list) from
     *  best to worst in the <code>ListView</code>.
     *  For each match, it will render the recognized phrase and the confidence with
     *  which it was recognized.
     */
    @SuppressLint("InlinedApi")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ASR_CODE)  {
            if (resultCode == RESULT_OK)  {
                if(data!=null) {
                    //Retrieves the N-best list and the confidences from the ASR result
                    ArrayList<String> nBestList = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    float[] nBestConfidences = null;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)  //Checks the API level because the confidence scores are supported only from API level 14
                        nBestConfidences = data.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES);

                    //Creates a collection of strings, each one with a recognition result and its confidence
                    //following the structure "Phrase matched (conf: 0.5)"
                    ArrayList<String> nBestView = new ArrayList<String>();

                    for(int i=0; i<nBestList.size(); i++){
                        if(nBestConfidences!=null){
                            if(nBestConfidences[i]>=0)
                                nBestView.add(nBestList.get(i) + " (conf: " + String.format("%.2f", nBestConfidences[i]) + ")");
                            else
                                nBestView.add(nBestList.get(i) + " (no confidence value available)");
                        }
                        else
                            nBestView.add(nBestList.get(i) + " (no confidence value available)");
                    }
                    String a=nBestList.get(0);
                    float coef=nBestConfidences[0];
                    for(int i=0; i<nBestList.size(); i++){
                        if(nBestConfidences[i]>coef){
                            coef=nBestConfidences[i];
                            a=nBestList.get(i);
                        }
                        Log.d(LOGTAG,"Lista: "+nBestList.get(i));
                    }


                    obtenerDireccion(nBestList);

                    textoRec= (TextView) findViewById(R.id.textRec);
                    textoRec.setText(Direccion +"/"+margen_error);
                    tts = new TextToSpeech(this, new OnInitListener() {
                        public void onInit(int status) {
                            if (status == TextToSpeech.SUCCESS) {

                                // Set language to US English if it is available
                                if (tts.isLanguageAvailable(Locale.US) >= 0)
                                    tts.setLanguage(Locale.US);
                            }

                        }
                    });
                    tts.speak(nBestList.get(0), TextToSpeech.QUEUE_ADD, null);

                    Log.i(LOGTAG, "There were : "+ nBestView.size()+" recognition results");
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

    private void obtenerDireccion(ArrayList<String> a){
        ArrayList<String> puntos_car=new ArrayList<String>();
        puntos_car.add("norte");
        puntos_car.add("sur");
        puntos_car.add("este");
        puntos_car.add("oeste");
        boolean coincide=false;
        Direccion=null;

        for(int i=0;i<puntos_car.size() && !coincide;i++){
            for(int j=0;j<a.size()&&!coincide;j++){
                if(a.get(j).length()>=puntos_car.get(i).length()) {
                    if (puntos_car.get(i).equals((a.get(j).substring(0, puntos_car.get(i).length())).toLowerCase())) {
                        coincide = true;
                        Direccion = puntos_car.get(i);
                    }
                }
            }
        }
        String numero;
        boolean es_num=false;
        if(Direccion!=null) {
            Pattern pat = Pattern.compile("[a-zA-Záéíóú]*(\\s)*[a-zA-Záéíóú]*");
            Matcher mat;
            for (int i = 0; i < a.size() && !es_num; i++) {
                mat= pat.matcher(a.get(i));
                numero=mat.replaceAll("");
                if(numero.length()>0){
                    Log.d(LOGTAG,"Margen: "+numero);
                    margen_error=Integer.parseInt(numero);

                    es_num=true;
                }
            }
        }
    }
}
