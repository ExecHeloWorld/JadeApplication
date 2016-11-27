package com.company;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
/**
 * Created by localadmin on 26.11.2016.
 */

public class Question extends Agent {
    @Override
    protected void setup() {
        System.out.print("Hi!");

        this.addBehaviour(new OfferRequestsServer());
        this.addBehaviour(new GetQuestionsServer());
    }

    @Override
    protected void takeDown() {

    }

    private class OfferRequestsServer extends CyclicBehaviour {
        @Override
        public void action() {

        }
    }

    private class GetQuestionsServer extends CyclicBehaviour {
        @Override
        public void action() {

        }
    }
}
