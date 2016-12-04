package com.company;
import jade.core.Agent;
import jade.core.Runtime;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;


import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import java.util.Scanner;
/**
 * Created by localadmin on 26.11.2016.
 */
public class ManagerTickets extends Agent {
    protected void setup() {
        Runtime rt = Runtime.instance();
        AgentContainer ac = getContainerController();

        BufferedReader br = null;
        int lineCount = 0;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream("startData.txt"), "utf8"));
            String s;
            while ((s = br.readLine()) != null) {
                lineCount++;

                String[] parametersFromString = s.split(";");
                String nameOfAgent = parametersFromString[0];
                String nameOfClass = "laba2." + parametersFromString[1];
                int arg2 = Integer.valueOf(parametersFromString[2]);
                int arg3 = Integer.valueOf(parametersFromString[3]);

                try {
                    AgentController aController = ac.createNewAgent(nameOfAgent, nameOfClass, new Object[]{nameOfAgent, arg2, arg3});
                    aController.start();
                } catch (Exception e) {
                    System.out.println(e);
                }
            }
        } catch (IOException ex) {
            System.out.println("Reading error in line " + lineCount);
            ex.printStackTrace();
        } finally {
            try {
                br.close();
            } catch (IOException ex) {
                System.out.println("Can not close");
            }
        }
    }
}