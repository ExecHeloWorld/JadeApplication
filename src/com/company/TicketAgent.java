package com.company;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

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
    static int sumOfComplexity = 0;
    static int n = 0;

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
        sd.setType("loading");
        sd.setName("JADE-loading");
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

        addBehaviour(new RequestForExchanging(this, 8000));

        addBehaviour(new OfferExchange());

        addBehaviour(new AcceptExchange());
    }

    private void deregister() {
        if (!isDeregister) {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            //sd.setType("deleting");
            sd.setType("loading");
            sd.setName("JADE-loading");
            template.addServices(sd);

            try {
                DFService.deregister(this, template);
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
                isDeregister = true;
            }
            catch (FIPAException fe) {
                fe.printStackTrace();
            }
        }
    }

    protected void takeDown()
    {
        try
        {
            DFService.deregister(this);
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }
        System.out.println("Ticket-agent "+getAID().getName()+" terminating.");
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
                String[] listOfComplexitis = msg.getContent().split(";");

                int neededComplexity = sumOfComplexity/counter - Complexity();

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
                String[] questionData = msg.getContent().split(";");
                String questionName = questionData[0];
                int questionComplexity = Integer.parseInt(questionData[1]);
                int section = Integer.parseInt(questionData[2]);
                Question candidateQuestion = new Question(questionName, questionComplexity, section);

                ACLMessage reply = msg.createReply();
                try
                {
                    addQuestion(candidateQuestion);
                    //sumOfRazn();
                    reply.setPerformative(ACLMessage.CONFIRM);
                    System.out.println("---------------------------------------------------------------");
                    System.out.println(questionName + " успешно добавлен к " + name);
                    System.out.println("От кого: " + msg.getSender().getName());
                    System.out.println("Сложность вопроса: " + questionComplexity);
                    System.out.println("Текущая сложность билета: " + Complexity());
                    System.out.println("Текущая кол-во вопросов: " + currentCount());
                    //System.out.println("Предельная сложность билета: " + limitComplexity);
                    System.out.println("---------------------------------------------------------------");

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

    protected class RequestForExchanging extends TickerBehaviour
    {

        public RequestForExchanging(Agent a, long period)
        {
            super(a, period);
        }

        protected void onTick() {

            if (delay == 0) {
                System.out.println(name + " закончил работу");
                //razn[id]=0;
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
            if ((Complexity() - (sumOfComplexity/counter)) > delta)
            {
                System.out.println(name + " - Превышена сложность ");

                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("loading");
                sd.setName("JADE-loading");
                template.addServices(sd);

                deregister();



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
            else
            if ((Complexity() - (sumOfComplexity/counter)) < -delta)
            {
                System.out.println(name + " - Недостаточно сложный");
                register();
            }
            else
            {
                System.out.println(name + " ОПТИМАЛЕН");
                deregister();


            }
        }

    }

    private class RequestForPlacing extends Behaviour
    {
        private int step = 0;
        private MessageTemplate mt;
        private Question deletedQuestion;
        @Override
        public void action()
        {
            switch (step)
            {
                case 0:
                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.setConversationId("exchange");

                    for (int i = 0; i < ticketAgents.length; ++i)
                    {
                        request.addReceiver(ticketAgents[i]);
                    }

                    int dif = Complexity() - sumOfComplexity/counter;

                    ArrayList<String> questionsForExchange = new ArrayList<>();
                    for (int i = 0; i < questions.size(); i++)
                    {
                        int weightCurrent = Complexity() - questions.get(i).Complexity();
                        if (Math.abs(weightCurrent - sumOfComplexity/counter) < dif)
                        {
                            questionsForExchange.add(String.valueOf(questions.get(i).Complexity()));
                        }
                    }

                    StringBuilder sb = new StringBuilder();

                    if (questionsForExchange.size() > 0)
                    {
                        for (int i = 0; i < questionsForExchange.size(); i++)
                        {
                            if (i == 0)
                            {
                                sb.append(questionsForExchange.get(i));
                            }
                            else
                            {
                                sb.append(";").append(questionsForExchange.get(i));
                            }
                        }

                        request.setContent(new String(sb));
                        request.setReplyWith("request"+System.currentTimeMillis());
                        myAgent.send(request);

                        mt = MessageTemplate.and(MessageTemplate.MatchConversationId("exchange"),
                                MessageTemplate.MatchInReplyTo(request.getReplyWith()));

                        step = 1;
                    }
                    break;
                case 1:
                    ACLMessage msg = myAgent.receive(mt);
                    if (msg != null) {
                        int selectedWeight = Integer.valueOf(msg.getContent());

                        for (Question question : questions)
                        {
                            if (selectedWeight == question.Complexity())
                            {
                                deletedQuestion = question;
                                break;
                            }
                        }

                        sb = new StringBuilder();
                        sb.append(deletedQuestion.Name()).append(";").append(deletedQuestion.Complexity()).append(";").append(deletedQuestion.Section());

                        ACLMessage reply = msg.createReply();
                        reply.setContent(new String(sb));
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setReplyWith("reply"+System.currentTimeMillis());
                        mt = MessageTemplate.and(MessageTemplate.MatchConversationId("exchange"),
                                MessageTemplate.MatchInReplyTo(reply.getReplyWith()));
                        myAgent.send(reply);
                        step = 2;
                    }
                    else
                    {
                        block();
                    }
                    break;
                case 2:
                    ACLMessage answer = myAgent.receive(mt);
                    if (answer != null)
                    {
                        if (answer.getPerformative() == ACLMessage.CONFIRM)
                        {
                            questions.remove(deletedQuestion);
                            //isOk = true;
                            System.out.println("ОБМЕН ПРОИЗОШЕЛ УСПЕШНО");
                        }
                        else
                        {
                            System.out.println("Не удалось произвести ОБМЕН");
                            n++;
                        }
                        step = 3;
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
            return (step == 3);
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
                        System.out.println("Откуда: " + msg.getSender().getName());
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
			/*synchronized (razn)
			{
			razn[id]=Math.pow(Complexity()-sumOfComplexity/counter,2);
			}*/
        }
        else
            throw new Exception("FUCK");
    }

	/*private double sumOfRazn()
	{
		double sum = 0;
		synchronized (razn)
		{

	for (double d: razn)
	{
		sum += d;
	}
		}
	sum = sum/counter;
	System.out.println(sum);
	return sum;
	}*/

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
