package com.example.demo;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/*
Give me six human-style rewrites of the prompt “explain to me how prompt caching works.” Keep the same informational depth and scope, but vary wording the way real people would phrase it differently (including technical and casual forms). These rewrites will be evaluated using cosine similarity, normalization, and Jaccard, so they should differ in surface form while still clearly matching the same intent.how do prompts get cached explain this.
*/
public class KeywordIdentifierV1 implements IKeywordIdentifier
{
    static final Pattern WORD = Pattern.compile("[a-z0-9]+");
    static final Pattern NUM = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");
    static final Pattern CODE_BLOCK = Pattern.compile("```[\\s\\S]*?```");
    static final Pattern INLINE_CODE = Pattern.compile("`[^`]*`");
    static final Pattern MATH = Pattern.compile("[=+\\-*/^]|\\b(sin|cos|tan|log|ln|sqrt|integral|derivative)\\b", Pattern.CASE_INSENSITIVE);

    static final Pattern NUM_UNIT = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\s*(mg|g|kg|mcg|ug|ml|l|oz|lb|lbs|km|m|cm|mm|ft|in|inch|inches|%|percent)\\b", Pattern.CASE_INSENSITIVE);
    static final Pattern MULTISPACE = Pattern.compile("\\s+");

    static final Set<String> STOP = Set.of(
            "the","a","an","and","or","to","of","in","on","for","with","is","are","was","were",
            "i","you","me","my","your","it","this","that","can","could","should","would","do","does","did",
            "what","why","how","when","where","will","might","may","vs","if","then","than","be","been","being",
            "let","like","just","really","please","help","need","want"
    );

    static final Set<String> KEEP = Set.of(
            "not","no","never","without","cant","cannot","dont","wont","isnt","arent","wasnt","werent",
            "too","very","more","less","much","enough","over","under","within","between",
            "per","each","every",
            "before","after","during",
            "day","daily","week","weekly","month","monthly","year","yearly"
    );

    static final Map<String, String> CANON = buildCanon();

    static Map<String, String> buildCanon() {
        Map<String, String> m = new HashMap<>();

        putAll(m, "safe", "safe","safer","safest","healthy","healthier","healthiest","ok","okay","fine","normal","acceptable","reasonable","recommended","recommendation","allowed");
        putAll(m, "danger", "dangerous","unsafe","risk","risky","harmful","toxic","poisonous");
        putAll(m, "good", "good","great","best","better","ideal","optimal");
        putAll(m, "bad", "bad","worse","worst","terrible","awful","horrible","poor");
        putAll(m, "fast", "fast","faster","fastest","quick","quicker","quickest","rapid","rapidly","speedy");
        putAll(m, "slow", "slow","slower","slowest","sluggish");
        putAll(m, "easy", "easy","easier","easiest","simple","simpler","simplest","straightforward");
        putAll(m, "hard", "hard","harder","hardest","difficult","tough","complex","complicated");
        putAll(m, "cheap", "cheap","cheaper","cheapest","inexpensive","budget","affordable");
        putAll(m, "expensive", "expensive","pricy","pricey","costly","premium","overpriced");

        putAll(m, "compare", "compare","comparison","vs","versus","difference","diff","contrast");
        putAll(m, "explain", "explain","explained","explanation","teach","teaching","walkthrough","clarify","clarification");
        putAll(m, "solve", "solve","solving","calculate","computed","compute","evaluation","evaluate","determine","find");
        putAll(m, "fix", "fix","debug","debugging","repair","resolve","resolved","issue","problem","broken","crash","error","exception","bug");

        return m;
    }

    static void putAll(Map<String, String> m, String canon, String... forms) {
        for (String f : forms) m.put(f, canon);
    }

    /**
     * Splits the prompt string into a quizk lookup hash set of words
     * @param s
     * @return
     */
    public Set<String> contentWords(String s)
    {
        s = normalizeIntent(s);
        Matcher m = WORD.matcher(s);
        Set<String> out = new HashSet<>();
        while (m.find()) {
            String w = m.group();
            if (w.length() >= 3 && (!STOP.contains(w) || KEEP.contains(w))) out.add(w);
        }
        return out;
    }

    /**
     * Find overlap words between 2 prompts
     * @param a string a
     * @param b
     * @return
     */
    public double jaccard(Set<String> a, Set<String> b)
    {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        int inter = 0;
        for (String x : a) if (b.contains(x)) inter++;
        int union = a.size() + b.size() - inter;
        if (union == 0) return 0.0;
        return (double) inter / union;
    }

    /**
     * Replace variable characteristics of prompt with static placeholders to normalize the intent
     * @param raw the raw prompt string
     * @return the normalized prompt string
     */
    public String normalizeIntent(String raw)
    {
        String s = raw == null ? "" : raw.trim();
        s = CODE_BLOCK.matcher(s).replaceAll(" <code> ");
        s = INLINE_CODE.matcher(s).replaceAll(" <code> ");
        s = MATH.matcher(s).replaceAll(" <math> ");
        s = s.toLowerCase(Locale.ROOT);

        s = s.replaceAll("n\\'t\\b", " not");
        s = s.replaceAll("\\bcan\\'t\\b", " cannot");
        s = s.replaceAll("\\bdon\\'t\\b", " do not");
        s = s.replaceAll("\\bwon\\'t\\b", " will not");

        s = NUM_UNIT.matcher(s).replaceAll("<num> $2");
        s = NUM.matcher(s).replaceAll("<num>");

        s = s.replaceAll("[^a-z0-9<>\\s]", " ");
        s = s.replaceAll("\\s+", " ").trim();

        s = stripLeadingPhrases(s);

        s = canonicalizeTokens(s);

        s = s.replaceAll("\\s+", " ").trim();
        return  s;
    }

    String stripLeadingPhrases(String s) {
        s = s.replaceAll("\\bplease\\b", " ");
        s = s.replaceAll("\\bcan you\\b", " ");
        s = s.replaceAll("\\bcould you\\b", " ");
        s = s.replaceAll("\\bhow do i\\b", " ");
        s = s.replaceAll("\\bhow can i\\b", " ");
        s = s.replaceAll("\\bwhat is\\b", " ");
        s = s.replaceAll("\\bwhy\\b", " ");
        s = s.replaceAll("\\bi need\\b", " ");
        s = s.replaceAll("\\bi want\\b", " ");
        s = s.replaceAll("\\bi would like\\b", " ");
        s = s.replaceAll("\\bhelp me\\b", " ");
        s = s.replaceAll("\\btell me\\b", " ");
        s = s.replaceAll("\\bshow me\\b", " ");
        s = MULTISPACE.matcher(s).replaceAll(" ").trim();
        return s;
    }

    String canonicalizeTokens(String s) {
        Matcher m = WORD.matcher(s);
        StringBuilder out = new StringBuilder();
        boolean first = true;
        while (m.find()) {
            String w = m.group();
            String cw = canonicalizeWord(w);
            if (cw.isEmpty()) continue;
            if (!first) out.append(' ');
            out.append(cw);
            first = false;
        }
        return out.toString();
    }

    String canonicalizeWord(String w) {
        if (w.equals("<num>") || w.equals("<code>") || w.equals("<math>")) return w;

        String direct = CANON.get(w);
        if (direct != null) return direct;

        String base = lightLemma(w);
        String mapped = CANON.get(base);
        if (mapped != null) return mapped;

        return base;
    }

    String lightLemma(String w) {
        int n = w.length();
        if (n <= 3) return w;

        if (w.endsWith("iest") && n > 5) return w.substring(0, n - 4) + "y";
        if (w.endsWith("ier") && n > 4) return w.substring(0, n - 3) + "y";

        if (w.endsWith("est") && n > 5) return w.substring(0, n - 3);
        if (w.endsWith("er") && n > 4) return w.substring(0, n - 2);

        if (w.endsWith("ing") && n > 5) return w.substring(0, n - 3);
        if (w.endsWith("ed") && n > 4) return w.substring(0, n - 2);

        if (w.endsWith("ies") && n > 4) return w.substring(0, n - 3) + "y";
        if (w.endsWith("es") && n > 4) return w.substring(0, n - 2);
        if (w.endsWith("s") && n > 3 && !w.endsWith("ss")) return w.substring(0, n - 1);

        return w;
    }

    /**
     * Detect which task the prompt is requesting for analysis datastring
     * @param s the raw prompt string
     * @return returns one of the task categories
     */
    public String detectTask(String s) {
        Map<String, Integer> score = new HashMap<>();

        score.put("summarize", score(s,
                "summarize","summary","summarise","tl;dr","tldr","shorten","condense","brief",
                "give me the gist","key points","highlights","bullet points","recap","overview",
                "make it shorter","too long","cliff notes","executive summary"
        ));

        score.put("translate", score(s,
                "translate","translation","in spanish","in french","in german","in italian","in portuguese",
                "in chinese","in japanese","in korean","in arabic","in hindi","in russian",
                "english to","to english","convert to","how do you say","what does this mean in",
                "romanize","romaji","pinyin","transliterate","subtitles"
        ));

        score.put("rewrite", score(s,
                "rewrite","rephrase","paraphrase","say it differently","word this","clean this up",
                "make this sound","improve wording","polish","tighten","edit","revise",
                "more formal","more casual","more professional","more friendly","more persuasive",
                "fix grammar","fix spelling","proofread","simplify","make it clearer"
        ));

        score.put("debug", score(s,
                "debug","fix","error","exception","crash","bug","not working","broken",
                "hang","freeze","stuck","timeout","fails","failing","won't compile","won't build",
                "stack trace","stacktrace","traceback","segfault","nullpointer","npe",
                "compile error","linker error","runtime error","test failed","flaky",
                "why is this happening","root cause","repro"
        ));

        score.put("troubleshoot", score(s,
                "troubleshoot","diagnose","help me","issue","problem","doesn't work",
                "how do i fix this","what's wrong","why won't it","setup issue","configuration",
                "install error","permission denied","port busy","connection refused","network issue",
                "can't connect","not detected","driver","firmware","bricked","won't turn on",
                "intermittent","randomly","keeps happening"
        ));

        score.put("explain", score(s,
                "explain","teach","walk me through","step by step","how does","how do",
                "why does","what is","meaning of","define","definition","concept",
                "intuition","derive","where does this come from","why is it like that",
                "eli5","in simple terms"
        ));

        score.put("solve", score(s,
                "solve","calculate","compute","evaluate","find","determine","what is the answer",
                "show work","work it out","plug in","simplify","derive equation","closed form",
                "integral","derivative","limit","series","probability","matrix","eigenvalue",
                "units","dimension","approximate","numerical","estimate","check my work"
        ));

        score.put("verify", score(s,
                "is this correct","is this right","check this","verify","validate","double check",
                "does this make sense","am i wrong","did i do this right","confirm",
                "prove","proof","counterexample","sanity check","spot mistakes"
        ));

        score.put("compare", score(s,
                "compare","difference","vs","versus","pros and cons","tradeoffs",
                "which is better","better than","contrast","rank","ranking","top vs",
                "advantages","disadvantages","strengths","weaknesses","side by side"
        ));

        score.put("recommend", score(s,
                "recommend","suggest","what should i","which should i","best","top","good",
                "pick one","help me choose","options","shortlist","worth it","buy",
                "for my use case","based on","budget","requirements","must have","nice to have"
        ));

        score.put("plan", score(s,
                "plan","roadmap","steps","strategy","approach","workflow","process",
                "how should i start","game plan","schedule","timeline","milestones",
                "prioritize","priority","next steps","what first","order of operations",
                "study plan","training plan","project plan"
        ));

        score.put("brainstorm", score(s,
                "brainstorm","ideas","idea","concepts","come up with","suggest ideas",
                "hackathon idea","project idea","startup idea","names","name ideas",
                "themes","angles","twists","variations","prompts","give me 10",
                "creative","unique","original","novel"
        ));

        score.put("generate_content", score(s,
                "write me","draft","compose","email","message","essay","paragraph","story",
                "script","dialogue","poem","lyrics","resume bullet","cover letter",
                "bio","about me","caption","tweet","post","statement of purpose",
                "pitch","press release","proposal"
        ));

        score.put("format_convert", score(s,
                "convert","transform","reformat","format","turn this into","change into",
                "to json","as json","to csv","as csv","to xml","yaml","markdown","latex",
                "table","spreadsheet","schema","parse","serialize","deserialize","pretty print",
                "minify","beautify","normalize","standardize"
        ));

        score.put("extract_structure", score(s,
                "extract","pull out","list all","identify","find all","entities","keywords",
                "topics","key terms","requirements","acceptance criteria","action items",
                "todo","todo","tasks","agenda","outline","sections","headings","metadata"
        ));

        score.put("classify_route", score(s,
                "classify","categorize","label","tag","assign","route",
                "what category","what domain","what type is this","belongs to",
                "detect intent","intent","topic","is this a","which bucket"
        ));

        score.put("design_architecture", score(s,
                "architecture","design","system design","database design","schema design",
                "api design","endpoints","contract","data model","uml","sequence diagram",
                "component diagram","microservice","monolith","scalability","latency",
                "throughput","caching","queue","event-driven","pubsub","broker",
                "auth","oauth","jwt","rate limit","load balancer"
        ));

        score.put("code_write", score(s,
                "write code","code this","implement","full code","complete code",
                "function","class","method","api endpoint","sql query","regex",
                "starter project","template","boilerplate","scaffold",
                "java code","c# code","python code","javascript code","typescript code",
                "example code","sample code","snippet"
        ));

        score.put("refactor_optimize", score(s,
                "refactor","clean up code","optimize","performance","speed up","make faster",
                "reduce memory","memory usage","big o","complexity","efficient",
                "improve","better way","simpler","remove duplication","deduplicate",
                "best practice","idiomatic","architecture improvement"
        ));

        score.put("test_qa", score(s,
                "test","unit test","integration test","write tests","test cases",
                "edge cases","corner cases","qa","quality","coverage",
                "mock","stub","assert","expected output","inputs outputs",
                "fuzz","property based"
        ));

        score.put("data_analysis", score(s,
                "analyze","analysis","trend","correlation","visualize","plot","graph",
                "statistics","mean","median","mode","stdev","standard deviation",
                "dataset","data frame","csv","spreadsheet","pivot","histogram",
                "forecast","predict","model","fit","regression"
        ));

        score.put("research_lookup", score(s,
                "look up","lookup","find sources","sources","citations","reference",
                "link","paper","study","research","latest","most recent",
                "what happened","news","update","release date","documentation","docs",
                "manual","spec","standard","datasheet"
        ));

        String bestTask = "general";
        int bestScore = 0;

        for (var e : score.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                bestTask = e.getKey();
            }
        }

        if (bestScore < 2) return "general";
        return bestTask;
    }

    public String detectDomain(String s)
    {
        String t = normalizeIntent(s);

        LinkedHashMap<String, String[]> K = new LinkedHashMap<>();

        K.put("coding", new String[]{
                "java","jdk","jre","jvm","spring","spring boot","hibernate","jpa","maven","gradle","kotlin","scala",
                "c#",".net","dotnet","asp.net","entity framework","ef core","wpf","winforms","avalonia","unity","xamarin",
                "python","pip","venv","conda","numpy","pandas","flask","django","fastapi",
                "javascript","typescript","node","npm","yarn","pnpm","react","next.js","vue","nuxt","angular","svelte",
                "html","css","tailwind","bootstrap",
                "sql","sqlite","mysql","postgres","postgresql","mariadb","mongodb","redis","cassandra","elasticsearch",
                "rest","graphql","grpc","websocket","http","https","json","xml","yaml",
                "docker","kubernetes","k8s","helm","terraform","ansible","ci","cd","github actions","gitlab ci","jenkins",
                "git","github","gitlab","bitbucket","merge","rebase","commit","pull request",
                "linux","ubuntu","debian","arch","bash","zsh","powershell","terminal","cli",
                "bug","debug","stack trace","stacktrace","exception","nullpointer","segfault","compile","compiler","linker",
                "thread","threads","concurrency","async","await","deadlock","race condition","memory leak",
                "api","sdk","library","package","dependency","unit test","integration test","junit","pytest","jest",
                "vscode","intellij","rider","visual studio","eclipse","xcode",
                "arduino","esp32","firmware","uart","spi","i2c","serial","embedded"
        });

        K.put("electronics", new String[]{
                "pn junction","p-n junction","diode","zener","rectifier","bridge rectifier",
                "semiconductor","doping","donor","acceptor","intrinsic","extrinsic","ni","vbi","vt","thermal voltage",
                "mosfet","nmos","pmos","bjt","npn","pnp","transistor","biasing","saturation","cutoff","active region",
                "op amp","opamp","operational amplifier","inverting","non-inverting","gain","slew rate","bandwidth",
                "resistor","capacitor","inductor","rlc","impedance","phasor","reactance","admittance",
                "ac circuit","dc circuit","kirchhoff","kcl","kvl","thevenin","norton","superposition",
                "power factor","rms","phase shift","frequency response","bode","filter","low pass","high pass","band pass",
                "transformer","relay","motor driver","h-bridge","pwm",
                "voltage regulator","lm7805","lm317","buck","boost","linear regulator","switching regulator",
                "oscilloscope","multimeter","logic analyzer","function generator",
                "ltspice","spice","schematic","pcb","breadboard","solder","flux",
                "vhdl","verilog","fpga","k-map","kmap","flip-flop","flip flop","latch","decoder","mux","demux","alu"
        });

        K.put("math", new String[]{
                "integral","differentiate","derivative","antiderivative","limit","continuity",
                "taylor","maclaurin","power series","series","convergence","ratio test","root test","comparison test",
                "sequence","sigma","summation","product",
                "vector","vector calculus","gradient","divergence","curl","line integral","surface integral","triple integral",
                "jacobian","cylindrical","spherical",
                "matrix","matrices","determinant","rank","inverse","rref","gaussian elimination","eigenvalue","eigenvector",
                "diagonalize","orthogonal","dot product","cross product",
                "ode","differential equation","separable","integrating factor","euler method","runge kutta",
                "laplace transform","fourier series","fourier transform",
                "probability","statistics","expected value","variance","standard deviation","normal distribution","binomial",
                "poisson","hypothesis test","p-value","confidence interval","regression",
                "proof","theorem","lemma","corollary",
                "<math>"
        });

        K.put("health", new String[]{
                "symptom","symptoms","diagnosis","treatment","clinic","doctor","urgent care","er","hospital",
                "fever","cough","sore throat","fatigue","nausea","vomit","diarrhea","pain","rash","infection","virus","bacteria",
                "flu","covid","strep","mono","pneumonia",
                "hiv","std","sti","chlamydia","gonorrhea","syphilis","herpes","hepatitis",
                "medication","antibiotic","antiviral","dose","dosage","side effect","contraindication","vaccine","shot",
                "blood test","lab results","rapid test",
                "anxiety","panic","depression","adhd","ocd","bipolar","therapy","counseling","psychiatrist","meditation",
                "sleep","insomnia","sleep apnea"
        });

        K.put("career", new String[]{
                "resume","résumé","cv","cover letter","job","internship","co-op","offer","salary","compensation",
                "interview","technical interview","behavioral interview","leetcode","hackerrank","oa","online assessment",
                "recruiter","hr","hiring manager","application","apply","referral","networking","linkedin","portfolio",
                "career fair","background check","intern","new grad","entry level",
                "promotion","performance review","raise","negotiation","two weeks notice","quit","layoff"
        });

        K.put("school", new String[]{
                "homework","assignment","lab report","lab","lecture","syllabus","canvas","blackboard",
                "exam","test","quiz","midterm","final",
                "class","course","professor","ta","office hours","grade","gpa","credit hours",
                "university","college","wvu","west virginia university","statler",
                "study guide","study plan","practice problems","rubric"
        });

        K.put("business", new String[]{
                "startup","mvp","pitch","deck","investor","vc","venture capital","funding","seed","series a","valuation",
                "market","market size","tam","sam","som","product-market fit","pmf","go-to-market","gtm",
                "marketing","branding","seo","ads","sales","pipeline","lead","conversion","churn","retention",
                "revenue","profit","margin","pricing","subscription","saas","b2b","b2c","kpi","okr",
                "business model","strategy","roadmap","competition","competitor","operations","logistics"
        });

        K.put("finance", new String[]{
                "budget","saving","savings","bank","checking","credit","credit card","debit","loan","interest rate","apr",
                "debt","student loan","mortgage","rent","insurance",
                "tax","irs","refund","deduction","w-2","1099",
                "invest","investment","stocks","stock","etf","index fund","mutual fund","bond","bonds","dividend",
                "options","calls","puts","portfolio","brokerage","roth","ira","401k",
                "crypto","bitcoin","ethereum","wallet","exchange"
        });

        K.put("relationships", new String[]{
                "dating","date","crush","girlfriend","boyfriend","partner","relationship","breakup","ex",
                "situationship","talking stage","ghosting","cheating","trust","jealousy",
                "marriage","engaged","fiancé","divorce",
                "communication","boundaries","attachment","love language","argument","conflict",
                "sex","intimacy","consent"
        });

        K.put("travel", new String[]{
                "travel","trip","vacation","itinerary","flight","airport","tsa","boarding pass","layover",
                "hotel","motel","hostel","airbnb","reservation","booking",
                "visa","passport","customs","immigration",
                "rental car","uber","lyft","train","bus","subway",
                "tour","tourism","things to do","attractions","restaurant recommendations","where to stay"
        });

        K.put("food", new String[]{
                "recipe","cook","cooking","bake","baking","ingredients","oven","stove","pan","pot",
                "meal","dinner","lunch","breakfast","snack","dessert",
                "restaurant","menu","cuisine","dish","flavor","spicy","sweet","salty","sour",
                "food safety","left out","expired","mold","undercooked",
                "calorie","calories","protein","carbs","fat","macros","nutrition facts","diet"
        });

        K.put("fitness", new String[]{
                "workout","gym","lifting","weights","strength","hypertrophy","cardio","conditioning",
                "bench","squat","deadlift","overhead press","pull-up","push-up",
                "program","split","push pull legs","ppl","upper lower","sets","reps","pr","1rm",
                "cut","bulk","lean bulk","bodyfat","calisthenics",
                "creatine","preworkout","whey","supplements"
        });

        K.put("entertainment", new String[]{
                "movie","film","tv","tv show","series","episode","season","finale","ending","spoiler",
                "netflix","hulu","disney+","hbo","max","paramount+","prime video",
                "actor","actress","director","cast","cinematography","soundtrack",
                "music","song","album","artist","playlist","spotify","apple music","soundcloud",
                "bpm","tempo","mixing","mastering","daw","ableton","fl studio","logic pro",
                "concert","tour","festival"
        });

        K.put("gaming", new String[]{
                "game","gaming","video game","pc game","console","ps5","playstation","xbox","nintendo","switch",
                "steam","epic","battle pass","ranked","matchmaking","fps","rpg","mmo","roguelike",
                "fortnite","minecraft","rust","gta","call of duty","valorant","league of legends",
                "controller","keyboard","mouse","graphics","fps","lag","ping","server"
        });

        K.put("art", new String[]{
                "art","design","graphic design","illustration","drawing","sketch","painting","color palette",
                "typography","font","logo","brand kit","poster","thumbnail",
                "ui","ux","wireframe","mockup","figma","adobe xd",
                "photoshop","illustrator","after effects","premiere",
                "blender","3d","modeling","render","shader","animation","rigging","texture"
        });

        K.put("legal_politics", new String[]{
                "law","legal","contract","agreement","terms of service","privacy policy","gdpr","hipaa",
                "copyright","dmca","trademark","patent","license","liability","lawsuit","court","judge",
                "government","politics","election","vote","voting","campaign","president","congress","senate",
                "policy","bill","supreme court","immigration","ice","deportation"
        });

        int best = 0;
        String bestKey = "other";

        for (Map.Entry<String, String[]> e : K.entrySet())
        {
            int score = score(t, e.getValue());
            if (score > best)
            {
                best = score;
                bestKey = e.getKey();
            }
        }

        return bestKey;
    }

    /**
     * Detect the output format for analysis datastring
     * @param s
     * @return
     */
    public String detectOutputFormat(String s)
    {
        if (containsAny(s, "json")) return "json";
        if (containsAny(s, "table")) return "table";
        if (containsAny(s, "bullet", "bullets", "list")) return "bullets";
        if (containsAny(s, "step by step", "steps")) return "steps";
        if (containsAny(s, "code", "<code>")) return "code";
        return "text";
    }


    static Set<String> tokens(String s) {
        s = s.toLowerCase(Locale.ROOT);
        Matcher m = WORD.matcher(s);
        Set<String> out = new HashSet<>();
        while (m.find()) out.add(m.group());
        return out;
    }

    static int score(String s, String... phrases) {
        String lower = s.toLowerCase(Locale.ROOT);
        String norm = " " + lower.replaceAll("[^a-z0-9]+", " ").trim() + " ";
        Set<String> tok = tokens(lower);

        int total = 0;

        for (String p : phrases) {
            if (p == null) continue;
            String pp = p.toLowerCase(Locale.ROOT).trim();
            if (pp.isEmpty()) continue;

            if (pp.contains(" ")) {
                String phrase = " " + pp.replaceAll("[^a-z0-9]+", " ").trim() + " ";
                if (phrase.length() < 5) continue;
                if (norm.contains(phrase)) total += 2;
            } else {
                String t = pp.replaceAll("[^a-z0-9]+", "");
                if (t.length() < 3) continue;
                if (tok.contains(t)) total += 1;
            }
        }

        return total;
    }

    static boolean containsAny(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    public Set<String> topKeywords(String s, int max)
    {
        s = normalizeIntent(s);
        Matcher m = WORD.matcher(s);
        Map<String, Integer> freq = new HashMap<>();
        while (m.find()) {
            String w = m.group();
            if (w.length() < 3) continue;
            if (STOP.contains(w) && !KEEP.contains(w)) continue;
            freq.put(w, freq.getOrDefault(w, 0) + 1);
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<>(freq.entrySet());
        list.sort((e1, e2) -> {
            int c = Integer.compare(e2.getValue(), e1.getValue());
            if (c != 0) return c;
            return e1.getKey().compareTo(e2.getKey());
        });
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < list.size() && out.size() < max; i++) out.add(list.get(i).getKey());
        return out;
    }




}
