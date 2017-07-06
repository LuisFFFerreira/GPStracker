package com.tsm.me.tp2_gps;


import java.util.List;
import java.util.Map;

public class RouteHelper {
    private String path_name;
    private boolean path_default;
    private Map<String,List<Double>> coords;

    RouteHelper(String s, boolean b){
        path_name = s;
        path_default = b;
        if(b) handleDefaultData();
    }

    private void handleDefaultData(){

    }
}
