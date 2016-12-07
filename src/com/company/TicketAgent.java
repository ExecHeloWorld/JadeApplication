package com.company;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.*;

import com.sun.javafx.scene.layout.region.Margins;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import javafx.util.Pair;

public class TicketAgent extends Agent
{
    private String name;
    private int questionsCount;
    private int currentCount;
    private ArrayList<Question> questions = new ArrayList<>();
    private boolean isDeregister = false;
    private int id;
    static double[] razn;
    static int counter = 0;
    static int  sumOfComplexity = 0;
    //static int n = 0;

    private int delta = 3;
    private int eps = 20;
    private int delay=3;

    private static boolean isFileOpen = false;
    private static int countOfWrittenTickets = 0;
    private static boolean isAverageWeightPrinted = false;

    private static FileWriter lockFile;

    static {
        try {
            lockFile = new FileWriter("result.txt", false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Object lockCounting1 = new Object();
    private static Object lockCounting2 = new Object();

    private static boolean isAmountOfLowCounted = false;
    private static int checker = 0;

    private AID[] ticketAgents;

    protected void setup() {
        Object[] args = getArguments();
		/*if(counter==0)
			razn=new double[(int) args[3]];
		id=counter++;*/
        counter++;
        name = (String) args[0];
        delta=(int) args[2];
        questionsCount = (int) args[1];

        System.out.println("Начал работу агент " + name + ", необходимое количество вопросов в билете - " + questionsCount);

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("GetQuestions");
        sd.setName(getLocalName());
        dfd.addServices(sd);
        try
        {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }

        addBehaviour(new OfferRequestsServer());

        addBehaviour(new PurchaseOrdersServer());

        addBehaviour(new RequestForExchanging(this, 10000));

        addBehaviour(new OfferExchange());

        addBehaviour(new AcceptExchange());
    }

    private void deregister() {
        if (!isDeregister) {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            //sd.setType("deleting");
            sd.setType("create");
            sd.setName("JADE-create");
            template.addServices(sd);

            try {
                DFService.deregister(this);
                isDeregister = true;
            }
            catch (FIPAException fe) {
                fe.printStackTrace();
            }
        }
    }

    private void register() {
        if (isDeregister) {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            //sd.setType("deleting");
            sd.setType("loading");
            sd.setName("JADE-loading");
            template.addServices(sd);

            try {
                DFService.register(this, template);
                isDeregister = false;
            }
            catch (FIPAException fe) {
                fe.printStackTrace();
            }
        }
    }

    protected void takeDown()
    {
        System.out.println("Ticket-agent "+getAID().getName()+" terminating.");
    }
    protected class RequestForExchanging extends TickerBehaviour
    {

        public RequestForExchanging(Agent a, long period)
        {
            super(a, period);
        }

        protected void onTick() {

            if (delay == 0) {
                System.out.println(name + " закончил работу");
                printInfo();
                this.stop();
                return;
            }
            delay--;

            System.out.println("---------------------------------------------------------------");
            //System.out.println("counter = " + counter);
            System.out.println("Средняя сложность: " + (sumOfComplexity/counter) + ", текущая сложность: " + Complexity() + "  текущее количество вопросов: " + questionsCount);
            System.out.println("Вопросы в " + name);
            for (Question question: questions)
            {
                System.out.println("     " + question.Name() + " из раздела " + question.Section() + " со сложностью " + question.Complexity());
            }


            if (Complexity() - (sumOfComplexity/counter) > delta)
            {
                System.out.println(name + " - Превышена сложность ");
                register();
                return;
            }

            if (Complexity() - (sumOfComplexity / counter) < -delta)
            {
                System.out.println(name + " - Недостаточно сложный");
                DFAgentDescription template = new DFAgentDescription();
                template.setName(getAID());
                ServiceDescription sd = new ServiceDescription();

                sd.setType("loading");
                sd.setName(getLocalName());
                template.addServices(sd);

                deregister();

                try
                {
                    DFAgentDescription[] result = DFService.search(myAgent, template);

                    ticketAgents = new AID[result.length];
                    for (int i = 0; i < ticketAgents.length; i++)
                    {
                        ticketAgents[i] = result[i].getName();
                        System.out.println(ticketAgents[i].toString() + " name");
                    }

                    myAgent.addBehaviour(new RequestForPlacing());
                }
                catch (FIPAException e)
                {
                    e.printStackTrace();
                }

                return;
            }
            else
            {
                System.out.println(name + " ОПТИМАЛЕН");
                deregister();
                //myAgent.doDelete();
                this.stop();
                return;
            }
        }

    }

    private class RequestForPlacing extends Behaviour
    {
        private int step = 0;
        private MessageTemplate mt;
        Pair<Question, Question> QuestionForReplace = new Pair<>(null, null);
        @Override
        public void action() {
            switch (step) {
                case 0:
                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.setConversationId("exchange");

                    StringBuilder sb = new StringBuilder();

                    for (int i = 0; i < ticketAgents.length; ++i) {
                        request.addReceiver(ticketAgents[i]);
                    }

                    try {
                        request.setContentObject((Serializable) questions);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    request.setReplyWith("request" + System.currentTimeMillis());
                    myAgent.send(request);
                    System.out.println(myAgent.getName() + "ЗАБАБАХАЛ ЗАПРОС БИЧАМ");

                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("exchange"),
                            MessageTemplate.MatchInReplyTo(request.getReplyWith()));

                    step = 1;

                    break;
                case 1:
                    ACLMessage msg = myAgent.receive(mt);

                    if (msg != null) {
                        try {
                            System.out.println(msg.getContentObject());
                            QuestionForReplace = (Pair<Question, Question>) msg.getContentObject();
                        } catch (UnreadableException e) {
                            e.printStackTrace();
                        }

                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        try {
                            reply.setContentObject((Serializable) QuestionForReplace);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        reply.setReplyWith("reply" + System.currentTimeMillis());
                        mt = MessageTemplate.and(MessageTemplate.MatchConversationId("exchange"),
                                MessageTemplate.MatchInReplyTo(reply.getReplyWith()));
                        myAgent.send(reply);

                       /* if (QuestionForReplace.getKey() != null) {
                            questions.remove(QuestionForReplace.getValue());
                            questions.add(QuestionForReplace.getKey());
                        }*/

                        /*int selectedWeight = Integer.valueOf(msg.getContent());

                        for (Question question : questions) {
                            if (selectedWeight == question.Complexity()) {
                                deletedQuestion = question;
                                break;
                            }
                        }

                        sb = new StringBuilder();
                        sb.append(deletedQuestion.Name()).append(";").append(deletedQuestion.Complexity()).append(";").append(deletedQuestion.Section());

                        ACLMessage reply = msg.createReply();
                        reply.setContent(new String(sb));
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setReplyWith("reply" + System.currentTimeMillis());
                        mt = MessageTemplate.and(MessageTemplate.MatchConversationId("exchange"),
                                MessageTemplate.MatchInReplyTo(reply.getReplyWith()));
                        myAgent.send(reply);*/
                        step = 2;
                    } else {
                        block();
                    }
                    break;
                case 2:
                    ACLMessage answer = myAgent.receive(mt);
                    if (answer != null) {
                        System.out.println(answer.getPerformative() + "        <-------------------" + ACLMessage.CONFIRM + " n ");
                        if (answer.getPerformative() == ACLMessage.CONFIRM && QuestionForReplace.getKey() != null && questions.remove(QuestionForReplace.getValue()))
                        {

                                questions.add(QuestionForReplace.getKey());
                                //questions.remove(deletedQuestion);
                                //isOk = true;
                                System.out.println("ОБМЕН ПРОИЗОШЕЛ УСПЕШНО");

                        } else {
                            System.out.println("Не удалось произвести ОБМЕН");
                            //n++;
                        }
                        step = 3;
                    } else {
                        block();
                    }
                    break;
            }
        }

        @Override
        public boolean done()
        {
            return (step == 3);
        }

    }

    private class OfferExchange extends CyclicBehaviour
    {
        @Override
        public void action()
        {
            MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REQUEST), MessageTemplate.MatchConversationId("exchange"));
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null)
            {
                ArrayList<Question> listQuestions = null;
                try {
                    listQuestions = (ArrayList<Question>) msg.getContentObject();
                }catch(Exception e){

                }
                System.out.println(listQuestions);

                ArrayList<Question> result = Question.GetSolutionPermutation(listQuestions, questions);
                ACLMessage reply = msg.createReply();

                Question tmp = null;
                if(!result.get(0).equals(listQuestions.get(0)))
                    tmp = result.get(0);
                else
                if(!result.get(1).equals(listQuestions.get(1)))
                    tmp = result.get(1);
                System.out.println(tmp.Name()+"                EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE");

                try {
                    reply.setContentObject((Serializable) new Pair<Question, Question>(tmp,questions.get(questions.indexOf(tmp))));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ArrayList<Question> newListQuestions = new ArrayList<>();
                newListQuestions.add(result.get(2));
                newListQuestions.add(result.get(3));

                questions = newListQuestions;
                System.out.println(questions);

                reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                myAgent.send(reply);
                /*int neededComplexity = sumOfComplexity/counter - Complexity();

                String bestComplexity = null;
                int bestDif = 99999;

                ArrayList<String> bestComplexitis = new ArrayList<>();

                for (String candidateQuestionComplexity : listOfComplexitis)
                {
                    if (Math.abs(neededComplexity - Integer.valueOf(candidateQuestionComplexity)) < bestDif)
                    {
                        bestComplexity = candidateQuestionComplexity;
                        bestDif = Math.abs(neededComplexity - Integer.valueOf(candidateQuestionComplexity));
                    }
                }

                ACLMessage reply = msg.createReply();
                reply.setContent(bestComplexity);
                reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                myAgent.send(reply);*/
            }
            else
            {
                block();
            }

        }

    }

    private class AcceptExchange extends CyclicBehaviour
    {

        @Override
        public void action()
        {
            MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId("exchange"));
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null)
            {
                //String[] questionData = msg.getContent().split(";");
                //String questionName = questionData[0];
                //int questionComplexity = Integer.parseInt(questionData[1]);
                //int section = Integer.parseInt(questionData[2]);
                Question candidateQuestion = null;
                try {
                    candidateQuestion = ((Pair<Question,Question>)msg.getContentObject()).getKey();
                } catch (UnreadableException e) {
                    System.out.println("ОТвалилсь на фазе чего-то кароче иди на хуй");
                    e.printStackTrace();
                }

                ACLMessage reply = msg.createReply();
                try
                {
                    if(questions.remove(((Pair<Question,Question>)msg.getContentObject()).getKey())) {
                        questions.add(((Pair<Question, Question>) msg.getContentObject()).getValue());
                        //addQuestion(candidateQuestion);
                        //sumOfRazn();
                        reply.setPerformative(ACLMessage.CONFIRM);
                        System.out.println("---------------------------------------------------------------");
                        System.out.println(candidateQuestion.Name() + " успешно добавлен к " + name);
                        System.out.println("От кого: " + msg.getSender().getName());
                        System.out.println("Сложность вопроса: " + candidateQuestion.Complexity());
                        System.out.println("Текущая сложность билета: " + Complexity());
                        System.out.println("Текущая кол-во вопросов: " + currentCount());
                        //System.out.println("Предельная сложность билета: " + limitComplexity);
                        System.out.println("---------------------------------------------------------------");
                    }else
                        reply.setPerformative(ACLMessage.FAILURE);
                }
                catch (Exception e)
                {
                    reply.setPerformative(ACLMessage.FAILURE);
                }

                myAgent.send(reply);
            }
            else
            {
                block();
            }

        }

    }



    private class OfferRequestsServer extends CyclicBehaviour {

        public void action() {

            if (currentCount() < questionsCount){
                MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REQUEST), MessageTemplate.MatchConversationId("placing"));
                ACLMessage msg = myAgent.receive(mt);
                if (msg != null) {
                    String[] questionData = msg.getContent().split(";");
                    String questionName = questionData[0];
                    int questionComplexity = Integer.parseInt(questionData[1]);
                    int questionSection = Integer.parseInt(questionData[2]);
                    Question candidateQuestion = new Question(questionName, questionComplexity, questionSection);

                    ACLMessage reply = msg.createReply();

                    if ((isLessThanLimitCount(candidateQuestion)) && !(hasSimilarQuestion(candidateQuestion)))
                        reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                    else
                        reply.setPerformative(ACLMessage.REFUSE);

                    myAgent.send(reply);
                }
                else
                {
                    block();
                }
            }
            else
            {
                block();
            }
        }

    }

    private class PurchaseOrdersServer extends CyclicBehaviour
    {

        public void action()
        {
            if (currentCount() < questionsCount){
                MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.CONFIRM), MessageTemplate.MatchConversationId("placing"));
                ACLMessage msg = myAgent.receive(mt);
                if (msg != null)
                {
                    ACLMessage reply = msg.createReply();

                    String[] questionData = msg.getContent().split(";");
                    String questionName = questionData[0];
                    int questionComplexity = Integer.parseInt(questionData[1]);
                    int questionSection = Integer.parseInt(questionData[2]);
                    Question candidateQuestion = new Question(questionName, questionComplexity, questionSection);


                    try
                    {
                        addQuestion(candidateQuestion);
                        if (!msg.getSender().getName().startsWith("Ticket"))
                            sumOfComplexity += questionComplexity;
                        reply.setPerformative(ACLMessage.INFORM);
                        System.out.println("---------------------------------------------------------------");
                        System.out.println(questionName + " успешно добавлен в " + name);
                        System.out.println("Из: " + msg.getSender().getName());
                        System.out.println("Сложность вопроса: " + questionComplexity);
                        System.out.println("Из раздела: " + questionSection);
                        System.out.println("Текущая сложность билета: " + Complexity());
                        System.out.println("Текущая кол-во вопросов: " + currentCount());
                        System.out.println("Необходимо кол-во вопросов в билете " + questionsCount);
                        System.out.println("---------------------------------------------------------------");

                    }
                    catch (Exception e)
                    {
                        reply.setPerformative(ACLMessage.FAILURE);
                    }
                    myAgent.send(reply);
                }
                else {
                    block();
                }
            }
            else
            {
                block();
            }
        }

    }

    private void printInfo() {
        synchronized (lockFile) {
            try {

                if (!isAverageWeightPrinted) {
                    int avg = sumOfComplexity/counter;
                    lockFile.write("Средняя сложность билетов: " + avg);
                    lockFile.write("\r\n");

                    isAverageWeightPrinted = true;
                }

                lockFile.write("-----------------------------------------------------");
                lockFile.write("\r\n");

                lockFile.write(name + " Суммарная сложность билета: " + Complexity());
                lockFile.write("\r\n");

                for (Question question : questions) {
                    lockFile.write("     " + question.Name() + " сложность " + question.Complexity() + " раздел " + question.Section());
                    lockFile.write("\r\n");
                }

                lockFile.write("-----------------------------------------------------");

                System.out.println(name + " written to the file");

                countOfWrittenTickets++;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }finally {
                this.doDelete();
            }

            if (countOfWrittenTickets == counter) {
                try {
                    lockFile.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    private void addQuestion(Question question) throws Exception
    {
        if ((isLessThanLimitCount(question)) && !(hasSimilarQuestion(question) ))
        {
            questions.add(question);
        }
        else
            throw new Exception("FUCK");
    }

    private boolean hasSimilarQuestion(Question candidateQuestion)
    {
        for (Question question : questions)
        {
            if (question.Section() == candidateQuestion.Section())
            {

                System.out.println("ОШИБКА СОВМЕСТИМОСТИ: " + candidateQuestion.Name() + " и " + question.Name()+" из одного раздела");
                return true;
            }
        }
        return false;
    }

    private boolean isLessThanLimitCount(Question question)
    {
        if (currentCount() < questionsCount)
            return true;
        else
        {
            return false;
        }
    }

    private int Complexity()
    {
        int complexity = 0;
        for (Question question : questions)
        {
            complexity += question.Complexity();
        }

        return complexity;
    }

    private int currentCount()
    {
        int count = 0;
        for (Question question : questions)
        {
            count += 1;
        }
        return count;
    }

    private int checkOptimality()
    {
        int currentPercentOfLoad = (int) (100 * Complexity() / questionsCount);
        if (currentPercentOfLoad > 80)
            return 1;
        else if (currentPercentOfLoad < 65)
            return -1;

        return 0;
    }
}
