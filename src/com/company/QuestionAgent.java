package com.company;

import java.util.HashSet;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class QuestionAgent extends Agent
{
    private String name;
    private int complexity;
    private int section;
    private boolean Placed = false;
    private boolean Working = false;
    private int stopDelay=5;

    private AID[] ticketAgents;

    protected void setup()
    {
        Object[] args = getArguments();
        name = (String) args[0];
        complexity = (int) args[1];
        section=(int) args[2];
        System.out.println("Начал работу агент " + name + " со сложностью " + complexity + " из раздела " + section);

        addBehaviour(new TickerBehaviour(this, 1000 * (1 + (int)Math.random() * 3))
        {

            @Override
            protected void onTick()
            {
                if (Placed || stopDelay==0)
                    this.stop();
                --stopDelay;
                //System.out.println(name + " пытается найти билет");

                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("GetQuestions");
                template.addServices(sd);

                try
                {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    ticketAgents = new AID[result.length];

                    for (int i = 0; i < ticketAgents.length; i++)
                    {
                        ticketAgents[i] = result[i].getName();
                    }
                    myAgent.addBehaviour(new RequestForPlacing());
                }
                catch (FIPAException e)
                {
                    e.printStackTrace();
                }
            }
        });
    }

    protected void takeDown()
    {
        System.out.println("QuestionAgent "+getAID().getName()+" terminating.");
    }

    private class RequestForPlacing extends Behaviour
    {
        private int step = 0;
        private MessageTemplate mt;
        private AID ticketAgent = null;
        StringBuilder sb = new StringBuilder();

        @Override
        public void action()
        {
            switch (step)
            {
                case 0:
                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    for (int i = 0; i < ticketAgents.length; ++i)
                    {
                        request.addReceiver(ticketAgents[i]);
                    }

                    sb.append(name).append(";").append(complexity).append(";").append(section);

                    request.setConversationId("placing");
                    request.setContent(new String(sb));
                    request.setReplyWith("request"+System.currentTimeMillis());
                    myAgent.send(request);

                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("placing"),
                            MessageTemplate.MatchInReplyTo(request.getReplyWith()));

                    step = 1;
                    break;
                case 1:
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null)
                    {
                        if (reply.getPerformative() == ACLMessage.ACCEPT_PROPOSAL)
                        {
                            ticketAgent = reply.getSender();
                            step = 2;
                        }
                    }
                    else
                    {
                        block();
                    }
                    break;
                case 2:
                    ACLMessage order = new ACLMessage(ACLMessage.CONFIRM);
                    order.setConversationId("placing");
                    order.addReceiver(ticketAgent);
                    order.setContent(new String(sb));
                    order.setReplyWith("order"+System.currentTimeMillis());
                    myAgent.send(order);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("placing"),
                            MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    step = 3;
                    break;
                case 3:
                    reply = myAgent.receive(mt);
                    if (reply != null)
                    {
                        if (reply.getPerformative() == ACLMessage.INFORM)
                        {
                            Placed = true;
                            myAgent.doDelete();
                        }
                        else
                        {
                            System.out.println("Ошибка при размещении вопроса");
                        }

                        step = 4;
                    }
                    else
                    {
                        block();
                    }
                    break;
            }
        }

        @Override
        public boolean done()
        {
            if ((step == 4) && (Placed == false))
            {
                System.out.println("Не удалось разместить");
            }
            return (step == 4);
        }

    }
}
