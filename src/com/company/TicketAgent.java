package com.company;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

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
    private boolean isDeregister = true;
    private int id;
    static double[] razn;
    static int counter = 0;
    static int  sumOfComplexity = 0;
    //static int n = 0;

    private int delta = 3;
    private int eps = 20;
    private int delay=6;

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
    private static String ComplexityType ="MoreComplexity";
    private static String StartToExchangeType = "StartToExchange";


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

        register("GetQuestions");
        addBehaviour(new OfferRequestsServer());

        addBehaviour(new PurchaseOrdersServer());

        addBehaviour(new OfferExchange());

        addBehaviour(new AcceptExchange());
    }

    private boolean contain(Question key, ArrayList<Question> questions) {
        for (int i = 0; i < questions.size(); ++i){
            if(key.Name().equals(questions.get(i).Name()))
                return true;
        }
        return false;
    }

    private void deregister() {
        if (!isDeregister) {
            try {
                DFService.deregister(this);
                isDeregister = true;
            }
            catch (FIPAException fe) {
                fe.printStackTrace();
            }finally {
                System.out.println("Получилось успешно разрегистрировать агента ->  " + this.getLocalName());
            }
        }else
            System.out.println("Не могу разрегистрировать агента ->  " + this.getLocalName());
    }

    private void register(String TypeName) {
        if (isDeregister) {
            DFAgentDescription template = new DFAgentDescription();
            template.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            //sd.setType("deleting");
            sd.setType(TypeName);
            sd.setName(getLocalName());
            template.addServices(sd);

            try {
                DFService.register(this, template);
                isDeregister = false;
            }
            catch (FIPAException fe) {
                fe.printStackTrace();
            }finally {
                System.out.println("Получилось успешно зарегистрировать агента ->  " + this.getLocalName());
            }
        }else
            System.out.println("Не могу зарегистрирвоать агента ->  " + this.getLocalName());
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
            synchronized (System.out) {
                System.out.println("---------------------------------------------------------------");

                //System.out.println("counter = " + counter);
                System.out.println("Средняя сложность: " + (sumOfComplexity / counter) + ", текущая сложность: " + Complexity() + "  текущее количество вопросов: " + questionsCount);
                System.out.println("Вопросы в " + name);
                for (Question question : questions) {
                    System.out.println("     " + question.Name() + " из раздела " + question.Section() + " со сложностью " + question.Complexity());
                }


                if (Complexity() - (sumOfComplexity / counter) > delta) {
                    System.out.println(name + " - Превышена сложность ");
                    deregister();
                    register(ComplexityType);
                    return;
                }

                if (Complexity() - (sumOfComplexity / counter) < -delta) {
                    deregister();
                    System.out.println(name + " - Недостаточно сложный");

                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType(ComplexityType);
                    template.addServices(sd);

                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        System.out.println("****");
                        System.out.println(result.length);
                        System.out.println("****");

                        ticketAgents = new AID[result.length];
                        for (int i = 0; i < ticketAgents.length; i++) {
                            ticketAgents[i] = result[i].getName();
                            System.out.println(ticketAgents[i].toString() + " name");
                        }

                        myAgent.addBehaviour(new RequestForPlacing());
                    } catch (FIPAException e) {
                        e.printStackTrace();
                    }

                    return;
                } else {
                    System.out.println(name + " ОПТИМАЛЕН");
                    deregister();
                    //myAgent.doDelete();
                    //this.stop();
                    return;
                }
            }
        }

    }

    private class RequestForPlacing extends Behaviour
    {
        Pair<Question, Question> QuestionForReplace = new Pair<>(null, null);
        private int step = 0;
        private MessageTemplate mt;
        @Override
        public void action() {
            synchronized (myAgent) {
                switch (step) {
                    case 0:
                        if (ticketAgents.length > 0) {
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

                            mt = MessageTemplate.and(MessageTemplate.MatchConversationId("exchange"),
                                    MessageTemplate.MatchInReplyTo(request.getReplyWith()));

                            step = 1;
                        }
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
                            synchronized (System.out) {
                                System.out.println("*******************");
                                System.out.println("Старый список вопросов в кортеже " + myAgent.getLocalName());
                                System.out.println("------> " + ((Question) QuestionForReplace.getKey()).Name().toString());
                                System.out.println("------> " + ((Question) QuestionForReplace.getValue()).Name());
                                System.out.println("*******************");
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
                            step = 2;
                        } else {
                            block();
                        }
                        break;
                    case 2:
                        ACLMessage answer = myAgent.receive(mt);
                        if (answer != null) {
                            System.out.println(answer.getPerformative() + "        <-------------------" + ACLMessage.CONFIRM + " n ");
                            int i =0;
                            if (answer.getPerformative() == ACLMessage.CONFIRM && QuestionForReplace.getKey() != null && contain(QuestionForReplace.getValue(), questions)) {
                                removeByName(QuestionForReplace.getValue(), questions);
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
        }

        @Override
        public boolean done()
        {
            return (step == 3);
        }

    }

    private void removeByName(Question value, ArrayList<Question> questions) {
        for(int i=0; i < questions.size(); ++i){
            if(questions.get(i).Name().equals(value.Name())){
                questions.remove(i);
                System.out.print("Удаление успено завершено!!!!!");
                return;
            }
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
                //System.out.println(listQuestions);

                ArrayList<Question> result = Question.GetSolutionPermutation(listQuestions, questions);
                ACLMessage reply = msg.createReply();

                Question tmp = null, tmp2 = null;

                synchronized (System.out) {
                    System.out.println("*******************");
                    for (int i = 0; i < result.size(); i++){
                        System.out.println("--->" + result.get(i).Name());
                    }
                    System.out.println("*******************");
                }


                if(!result.get(0).Name().equals(listQuestions.get(0).Name())) {
                    tmp = result.get(0);
                    tmp2 = result.get(1);
                }

                if(!result.get(1).Name().equals(listQuestions.get(1).Name())) {
                    tmp = result.get(1);
                    tmp2 = result.get(0);
                }

                try {
                    reply.setContentObject((Serializable) new Pair<Question, Question>(tmp,tmp2));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                myAgent.send(reply);

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
                Question candidateQuestion = null;
                try {
                    candidateQuestion = ((Pair<Question,Question>)msg.getContentObject()).getValue();
                } catch (UnreadableException e) {
                    System.out.println("ОТвалилсь на фазе чего-то кароче иди на хуй");
                    e.printStackTrace();
                }

                ACLMessage reply = msg.createReply();
                try
                {
                    System.out.println("*******************");
                    System.out.println("Вопросы получателя");
                    for (int i = 0; i < questions.size(); i++){
                        System.out.println("--->" + questions.get(i).Name());
                    }
                    System.out.println("*******************");

                    if(contain(((Pair<Question, Question>) msg.getContentObject()).getKey(), questions)) {
                        removeByName(((Pair<Question, Question>) msg.getContentObject()).getKey(), questions);
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

        boolean flag = true;
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
                if(flag) {
                    deregister();
                    register(StartToExchangeType);

                    addBehaviour(new RequestForExchanging(myAgent, 5000));
                    flag = false;
                }
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

}
