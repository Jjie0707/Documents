package jjie.job_spring.interview;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import jjie.job_spring.auth.common.AppUserDetails;
import jjie.job_spring.user.common.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:interview-questions;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Transactional
@Sql(scripts = {"/sql/users-test.sql", "/sql/auth-test.sql", "/sql/interview-rounds-test.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class InterviewQuestionIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    private AppUserDetails owner;
    private AppUserDetails other;
    private Long jobId;
    private Long target;
    private Long sibling;
    private Long ownOtherJobRound;
    private Long foreignRound;

    @BeforeEach
    void seedOnlyIsolatedH2() {
        owner = seedUser("question_demo_a"); other = seedUser("question_demo_b");
        jobId = seedJob(owner, "虚构问题公司");
        target = seedRound(owner, jobId, 1); sibling = seedRound(owner, jobId, 2);
        ownOtherJobRound = seedRound(owner, seedJob(owner, "另一虚构公司"), 1);
        foreignRound = seedRound(other, seedJob(other, "他人虚构公司"), 1);
    }

    @Test
    void createsNormalizesAndReadsWithoutChangingRoundOrJob() throws Exception {
        var beforeRound = jdbc.queryForMap("SELECT * FROM interview_rounds WHERE id=?", target);
        var beforeJob = jdbc.queryForMap("SELECT * FROM job_applications WHERE id=?", jobId);
        create(target, Map.of("question", " \u2003事务何时回滚？\n如何验证？ ", "answerNotes", "  运行时异常\n事务边界  ",
                        "knowledgeTopic", "  Spring 事务  ", "reviewNotes", "  用独立事务验证  "))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.question").value("事务何时回滚？\n如何验证？"))
                .andExpect(jsonPath("$.answerNotes").value("运行时异常\n事务边界"))
                .andExpect(jsonPath("$.knowledgeTopic").value("Spring 事务"))
                .andExpect(jsonPath("$.reviewNotes").value("用独立事务验证"))
                .andExpect(jsonPath("$.userId").doesNotExist()).andExpect(jsonPath("$.interviewRoundId").doesNotExist());
        read(target).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].question").value("事务何时回滚？\n如何验证？"))
                .andExpect(jsonPath("$.items[0].answerNotes").value("运行时异常\n事务边界"));
        assertThat(jdbc.queryForMap("SELECT * FROM interview_rounds WHERE id=?", target)).isEqualTo(beforeRound);
        assertThat(jdbc.queryForMap("SELECT * FROM job_applications WHERE id=?", jobId)).isEqualTo(beforeJob);
        assertThat(jdbc.queryForObject("SELECT user_id FROM interview_questions WHERE interview_round_id=?", Long.class, target)).isEqualTo(owner.getId());
    }

    @Test
    void emptyRoundIsSuccessfulAndOptionalValuesBecomeNull() throws Exception {
        read(target).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.totalPages").value(0));
        create(target, Map.of("question", "只填问题")).andExpect(status().isCreated());
        rawCreate(target, "{\"question\":\"选填空白\",\"answerNotes\":null,\"knowledgeTopic\":\" \u2003\",\"reviewNotes\":\"\\n\"}")
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_questions WHERE answer_notes IS NULL AND knowledge_topic IS NULL AND review_notes IS NULL", Long.class)).isEqualTo(2);
    }

    @Test
    void listIsScopedToRoundAndCurrentUserAndNewestFirstWithDatabasePagination() throws Exception {
        for (int i = 1; i <= 11; i++) create(target, Map.of("question", "虚构问题" + i)).andExpect(status().isCreated());
        create(sibling, Map.of("question", "同岗位其他轮次")).andExpect(status().isCreated());
        create(ownOtherJobRound, Map.of("question", "本人其他岗位")).andExpect(status().isCreated());
        mvc.perform(post(path(foreignRound)).with(user(other)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"他人问题\"}"))
                .andExpect(status().isCreated());
        read(target).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.total").value(11)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].question").value("虚构问题11"))
                .andExpect(jsonPath("$.items[9].question").value("虚构问题2"));
        for (String page : new String[]{"2", "2147483647"}) {
            mvc.perform(get(path(target)).with(user(owner)).param("page", page))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2))
                    .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].question").value("虚构问题1"));
        }
        read(sibling).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].question").value("同岗位其他轮次"));
    }

    @Test
    void forgedIdsCannotChangeOwnerTargetOrExistingQuestion() throws Exception {
        create(sibling, Map.of("question", "保留的原问题")).andExpect(status().isCreated());
        Long existing = jdbc.queryForObject("SELECT id FROM interview_questions WHERE interview_round_id=?", Long.class, sibling);
        mvc.perform(post(path(target)).with(user(owner)).with(csrf()).param("userId", other.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "id", existing, "userId", other.getId(), "interviewRoundId", foreignRound, "question", "新增内容"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(existing.intValue())));
        assertThat(jdbc.queryForObject("SELECT question FROM interview_questions WHERE id=?", String.class, existing)).isEqualTo("保留的原问题");
        assertThat(jdbc.queryForObject("SELECT user_id FROM interview_questions WHERE interview_round_id=?", Long.class, target)).isEqualTo(owner.getId());
        assertThat(count()).isEqualTo(2);
    }

    @Test
    void missingAndForeignParentHaveSame404AndCannotReadOrWrite() throws Exception {
        create(target, Map.of("question", "本人虚构问题")).andExpect(status().isCreated());
        String missingRead = read(Long.MAX_VALUE).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ROUND_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String foreignRead = read(foreignRound).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(foreignRead).isEqualTo(missingRead);
        String missingWrite = create(Long.MAX_VALUE, Map.of("question", "不得保存"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String foreignWrite = create(foreignRound, Map.of("question", "不得保存"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(foreignWrite).isEqualTo(missingWrite);
        assertThat(count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"question\":null}", "{\"question\":\"  \\n\"}", "{\"question\":\"\u2003\"}"})
    void questionIsRequired(String body) throws Exception {
        rawCreate(target, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.question").isString());
        assertThat(count()).isZero();
    }

    static Stream<Arguments> textLimits() {
        return Stream.of(Arguments.of("question", 2000), Arguments.of("answerNotes", 4000),
                Arguments.of("knowledgeTopic", 100), Arguments.of("reviewNotes", 2000));
    }

    @ParameterizedTest
    @MethodSource("textLimits")
    void textBoundaryAndOverLimitAreValidatedWithoutEchoingContent(String field, int limit) throws Exception {
        var values = new java.util.HashMap<String, Object>(); values.put("question", "虚构问题");
        values.put(field, "测".repeat(limit)); create(target, values).andExpect(status().isCreated());
        values.put(field, "DO_NOT_ECHO" + "测".repeat(limit));
        String body = create(target, values).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields." + field).isString())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("DO_NOT_ECHO"); assertThat(count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{\"question\":{\"secret\":\"DO_NOT_ECHO\"}}", "{\"question\":[]}"})
    void malformedBodyIsSanitized400(String body) throws Exception {
        String response = rawCreate(target, body).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("DO_NOT_ECHO", "Json", "Exception"); assertThat(count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "2147483648", "DO_NOT_ECHO"})
    void invalidPageIsSanitized400(String page) throws Exception {
        String body = mvc.perform(get(path(target)).with(user(owner)).param("page", page))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.page").isString())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("DO_NOT_ECHO", "NumberFormat", "Failed to convert");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DO_NOT_ECHO", "9223372036854775808"})
    void malformedRoundIdIsSanitized400ForBothMethods(String id) throws Exception {
        String endpoint = "/api/interview-rounds/" + id + "/questions";
        String read = mvc.perform(get(endpoint).with(user(owner))).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        String write = mvc.perform(post(endpoint).with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"虚构问题\"}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(read + write).doesNotContain("DO_NOT_ECHO", "NumberFormat"); assertThat(count()).isZero();
    }

    @Test
    void requiresLoginAndValidCsrf() throws Exception {
        mvc.perform(get(path(target))).andExpect(status().isUnauthorized());
        mvc.perform(post(path(target)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"虚构问题\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path(target)).with(user(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"虚构问题\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path(target)).with(user(owner)).with(csrf().useInvalidToken()).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"虚构问题\"}"))
                .andExpect(status().isForbidden());
        assertThat(count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"round", "job"})
    void createdQuestionIsCleanedByExistingRoundOrJobDelete(String kind) throws Exception {
        create(target, Map.of("question", "应清理的虚构问题")).andExpect(status().isCreated());
        create(ownOtherJobRound, Map.of("question", "应保留的虚构问题")).andExpect(status().isCreated());
        mvc.perform(delete("round".equals(kind) ? "/api/interview-rounds/" + target : "/api/jobs/" + jobId)
                        .with(user(owner)).with(csrf())).andExpect(status().isNoContent());
        read(target).andExpect(status().isNotFound());
        read(ownOtherJobRound).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].question").value("应保留的虚构问题"));
        assertThat(count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"question", "answerNotes", "knowledgeTopic", "reviewNotes"})
    void keywordMatchesEachFieldIgnoringCaseAndTrimmingEdges(String field) throws Exception {
        var values = new java.util.HashMap<String, Object>();
        values.put("question", "普通问题"); values.put(field, "前缀 Spring 后缀");
        create(target, values).andExpect(status().isCreated());
        create(target, Map.of("question", "无关问题")).andExpect(status().isCreated());
        search(" \u2003sPrInG\n").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0]." + field).value("前缀 Spring 后缀"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "\\", "' OR 1=1 --", "!%_"})
    void keywordTreatsWildcardsEscapeAndSqlLookingInputLiterally(String keyword) throws Exception {
        create(target, Map.of("question", "前缀" + keyword + "后缀")).andExpect(status().isCreated());
        create(target, Map.of("question", "完全无关的内容")).andExpect(status().isCreated());
        search(keyword).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].question").value("前缀" + keyword + "后缀"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \u2003\n", "   "})
    void blankKeywordUsesOrdinaryPagination(String keyword) throws Exception {
        create(target, Map.of("question", "普通问题")).andExpect(status().isCreated());
        search(keyword).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void searchOrBranchesCannotEscapeUserAndRoundScope() throws Exception {
        create(target, Map.of("question", "目标问题", "reviewNotes", "命中")).andExpect(status().isCreated());
        create(sibling, Map.of("question", "命中同岗位其他轮次")).andExpect(status().isCreated());
        create(ownOtherJobRound, Map.of("question", "其他岗位", "answerNotes", "命中")).andExpect(status().isCreated());
        mvc.perform(post(path(foreignRound)).with(user(other)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("question", "他人问题", "knowledgeTopic", "命中"))))
                .andExpect(status().isCreated());
        search("命中").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].question").value("目标问题"));
        mvc.perform(get(path(foreignRound)).with(user(owner)).param("keyword", "命中"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ROUND_NOT_FOUND"));
        mvc.perform(get(path(target)).param("keyword", "命中")).andExpect(status().isUnauthorized());
    }

    @Test
    void filteredCountPageAndFocusUseSameConditions() throws Exception {
        for (int i = 1; i <= 11; i++) {
            create(target, Map.of("question", "匹配" + i)).andExpect(status().isCreated());
            create(target, Map.of("question", "不相关" + i)).andExpect(status().isCreated());
        }
        Long oldestMatch = jdbc.queryForObject("SELECT MIN(id) FROM interview_questions WHERE question LIKE '匹配%'", Long.class);
        Long excluded = jdbc.queryForObject("SELECT MAX(id) FROM interview_questions WHERE question LIKE '不相关%'", Long.class);
        search("匹配").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.total").value(11)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].question").value("匹配11"));
        mvc.perform(get(path(target)).with(user(owner)).param("keyword", "匹配").param("questionId", oldestMatch.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.items[0].question").value("匹配1"));
        for (Long focus : new Long[]{excluded, Long.MAX_VALUE}) {
            mvc.perform(get(path(target)).with(user(owner)).param("keyword", "匹配").param("questionId", focus.toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1));
        }
        mvc.perform(get(path(target)).with(user(owner)).param("keyword", "匹配").param("page", "2147483647"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2));
        search("没有这样的内容").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void writesRecalculateFilteredResultsWithoutChangingOtherQuestions() throws Exception {
        for (int i = 1; i <= 11; i++) create(target, Map.of("question", "事务问题" + i)).andExpect(status().isCreated());
        Long oldest = jdbc.queryForObject("SELECT MIN(id) FROM interview_questions WHERE interview_round_id=?", Long.class, target);
        mvc.perform(put("/api/interview-questions/" + oldest).with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"已改为集合问题\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(path(target)).with(user(owner)).param("keyword", "事务").param("page", "2").param("questionId", oldest.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(10)).andExpect(jsonPath("$.page").value(1));
        mvc.perform(delete("/api/interview-questions/" + oldest).with(user(owner)).with(csrf())).andExpect(status().isNoContent());
        search("事务").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(10));
        create(target, Map.of("question", "新增事务问题")).andExpect(status().isCreated());
        search("事务").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(11))
                .andExpect(jsonPath("$.items[0].question").value("新增事务问题"));
    }

    @Test
    void keywordValidatesUnicodeCodePointsAndDoesNotEchoRejectedContent() throws Exception {
        String accepted = "😀".repeat(100);
        create(target, Map.of("question", accepted)).andExpect(status().isCreated());
        search(" " + accepted + " ").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        String body = search("PRIVATE_SEARCH" + accepted).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.keyword").value("搜索关键词不能超过100个字符"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("PRIVATE_SEARCH", "😀", "Exception");
        assertThat(new jjie.job_spring.interview.common.InterviewQuestionPageQuery(null, null, accepted, null).toString())
                .doesNotContain(accepted);
        assertThat(count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \u2003\n", "   "})
    void blankTopicDoesNotRestrictKeywordResults(String topic) throws Exception {
        create(target, Map.of("question", "事务一", "knowledgeTopic", "Spring")).andExpect(status().isCreated());
        create(target, Map.of("question", "事务二")).andExpect(status().isCreated());
        filter("事务", topic).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.topics[0]").value("Spring"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Java", "%_!", "' OR 1=1 --", "\\"})
    void topicIsFullLiteralNameWithTrimAndCaseInsensitiveMatching(String topic) throws Exception {
        create(target, Map.of("question", "完整主题", "knowledgeTopic", topic)).andExpect(status().isCreated());
        create(target, Map.of("question", "只包含主题", "knowledgeTopic", topic + " 扩展")).andExpect(status().isCreated());
        create(target, Map.of("question", "未填主题")).andExpect(status().isCreated());
        filter("", " \u2003" + topic.toLowerCase(java.util.Locale.ROOT) + "\n").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].question").value("完整主题"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"question", "answerNotes", "knowledgeTopic", "reviewNotes"})
    void topicAndEachKeywordBranchAreIntersectedWithoutEscapingScope(String field) throws Exception {
        var values = new java.util.HashMap<String, Object>();
        values.put("question", "目标问题"); values.put(field, "Spring"); values.put("knowledgeTopic", "Spring");
        create(target, values).andExpect(status().isCreated());
        // 同关键词不同主题、同主题不同关键词，以及同主题同关键词的其他归属均不能进入结果。
        create(target, Map.of("question", "Spring", "knowledgeTopic", "Java")).andExpect(status().isCreated());
        create(target, Map.of("question", "其他问题", "knowledgeTopic", "Spring")).andExpect(status().isCreated());
        create(sibling, Map.of("question", "Spring", "knowledgeTopic", "Spring")).andExpect(status().isCreated());
        create(ownOtherJobRound, Map.of("question", "Spring", "knowledgeTopic", "Spring")).andExpect(status().isCreated());
        mvc.perform(post(path(foreignRound)).with(user(other)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("question", "Spring", "knowledgeTopic", "Spring"))))
                .andExpect(status().isCreated());
        // knowledgeTopic 本身属于关键词搜索字段，因而另一条 Spring 主题也应匹配 Spring 关键词。
        filter("Spring", "spring").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
        String uniqueKeyword = "唯一命中";
        values.put(field, uniqueKeyword);
        String selectedTopic = field.equals("knowledgeTopic") ? uniqueKeyword : "Spring";
        values.put("knowledgeTopic", selectedTopic);
        create(target, values).andExpect(status().isCreated());
        create(target, Map.of("question", uniqueKeyword, "knowledgeTopic", "不同主题")).andExpect(status().isCreated());
        filter(uniqueKeyword, selectedTopic).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void topicOptionsCoverWholeOwnedRoundIndependentOfKeywordTopicAndPage() throws Exception {
        for (int i = 1; i <= 11; i++) create(target, Map.of("question", "问题" + i, "knowledgeTopic", "Java")).andExpect(status().isCreated());
        create(target, Map.of("question", "其他主题", "knowledgeTopic", "Spring")).andExpect(status().isCreated());
        create(target, Map.of("question", "同名大小写", "knowledgeTopic", "java")).andExpect(status().isCreated());
        create(target, Map.of("question", "无主题")).andExpect(status().isCreated());
        create(sibling, Map.of("question", "不可泄露", "knowledgeTopic", "SIBLING_TOPIC")).andExpect(status().isCreated());
        create(ownOtherJobRound, Map.of("question", "不可泄露", "knowledgeTopic", "OTHER_JOB_TOPIC")).andExpect(status().isCreated());
        mvc.perform(post(path(foreignRound)).with(user(other)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"他人问题\",\"knowledgeTopic\":\"FOREIGN_TOPIC\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get(path(target)).with(user(owner)).param("topic", "Java").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.topics", org.hamcrest.Matchers.contains("Java", "Spring")));
        for (String selected : new String[]{"Spring", "不存在的主题"}) {
            filter("没有匹配的词", selected).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                    .andExpect(jsonPath("$.topics", org.hamcrest.Matchers.contains("Java", "Spring")));
        }
    }

    @Test
    void combinedFilterCountRowsAndQuestionFocusUseSameConditions() throws Exception {
        for (int i = 1; i <= 11; i++) {
            create(target, Map.of("question", "命中" + i, "knowledgeTopic", "Java")).andExpect(status().isCreated());
            create(target, Map.of("question", "命中但不同主题" + i, "knowledgeTopic", "Spring")).andExpect(status().isCreated());
        }
        create(target, Map.of("question", "无关内容", "knowledgeTopic", "Java")).andExpect(status().isCreated());
        Long oldest = jdbc.queryForObject("SELECT MIN(id) FROM interview_questions WHERE knowledge_topic='Java'", Long.class);
        Long excluded = jdbc.queryForObject("SELECT MAX(id) FROM interview_questions WHERE knowledge_topic='Spring'", Long.class);
        Long siblingId = json.readTree(create(sibling, Map.of("question", "命中其他轮", "knowledgeTopic", "Java"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        filter("命中", "java").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(11))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].question").value("命中11"));
        mvc.perform(get(path(target)).with(user(owner)).param("keyword", "命中").param("topic", "java").param("questionId", oldest.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.items[0].id").value(oldest));
        for (Long focus : new Long[]{excluded, siblingId}) {
            mvc.perform(get(path(target)).with(user(owner)).param("keyword", "命中").param("topic", "Java").param("questionId", focus.toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1));
        }
        mvc.perform(get(path(target)).with(user(owner)).param("keyword", "命中").param("topic", "Java").param("page", "2147483647"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2));
    }

    @Test
    void topicOptionsAndFilterResultsReflectCreateUpdateAndDelete() throws Exception {
        create(target, Map.of("question", "原问题", "knowledgeTopic", "Java")).andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM interview_questions WHERE interview_round_id=?", Long.class, target);
        mvc.perform(put("/api/interview-questions/" + id).with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"已修改\",\"knowledgeTopic\":\"Spring\"}"))
                .andExpect(status().isOk());
        filter("", "Java").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.topics", org.hamcrest.Matchers.contains("Spring")));
        filter("", "Spring").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(delete("/api/interview-questions/" + id).with(user(owner)).with(csrf())).andExpect(status().isNoContent());
        filter("", "Spring").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.topics").isEmpty());
        create(target, Map.of("question", "新问题", "knowledgeTopic", "事务")).andExpect(status().isCreated());
        filter("", "事务").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.topics", org.hamcrest.Matchers.contains("事务")));
    }

    @Test
    void topicLengthUsesCodePointsAndDoesNotEchoRejectedValue() throws Exception {
        String accepted = "😀".repeat(100);
        filter("", " " + accepted + " ").andExpect(status().isOk()).andExpect(jsonPath("$.topics").isEmpty());
        String body = filter("", "PRIVATE_TOPIC" + accepted).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.topic").value("知识主题不能超过100个字符"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("PRIVATE_TOPIC", "😀", "Exception");
        assertThat(new jjie.job_spring.interview.common.InterviewQuestionPageQuery(null, null, null, accepted).toString())
                .doesNotContain(accepted);
        assertThat(count()).isZero();
    }

    @Test
    void topicQueryKeepsParent404AndAuthenticationWithoutExposingOptions() throws Exception {
        for (Long round : new Long[]{Long.MAX_VALUE, foreignRound}) {
            mvc.perform(get(path(round)).with(user(owner)).param("topic", "PRIVATE_TOPIC"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ROUND_NOT_FOUND"))
                    .andExpect(jsonPath("$.topics").doesNotExist());
        }
        mvc.perform(get(path(target)).param("topic", "Java")).andExpect(status().isUnauthorized());
    }

    private ResultActions filter(String keyword, String topic) throws Exception {
        return mvc.perform(get(path(target)).with(user(owner)).param("keyword", keyword).param("topic", topic));
    }

    private ResultActions search(String keyword) throws Exception {
        return mvc.perform(get(path(target)).with(user(owner)).param("keyword", keyword));
    }

    private ResultActions create(Long id, Map<String, ?> values) throws Exception { return rawCreate(id, json.writeValueAsString(values)); }
    private ResultActions rawCreate(Long id, String body) throws Exception {
        return mvc.perform(post(path(id)).with(user(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private ResultActions read(Long id) throws Exception { return mvc.perform(get(path(id)).with(user(owner))); }
    private String path(Long id) { return "/api/interview-rounds/" + id + "/questions"; }
    private long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM interview_questions", Long.class); }
    private AppUserDetails seedUser(String username) {
        String credential = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users(username,password_hash) VALUES (?,?)", username, credential);
        UserAccount account = new UserAccount(); account.setUsername(username); account.setPasswordHash(credential);
        account.setId(jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username));
        return new AppUserDetails(account);
    }
    private Long seedJob(AppUserDetails identity, String company) {
        jdbc.update("INSERT INTO job_applications(user_id,company_name,job_title) VALUES (?,?,'虚构问题岗位')", identity.getId(), company);
        return jdbc.queryForObject("SELECT id FROM job_applications WHERE user_id=? AND company_name=?", Long.class, identity.getId(), company);
    }
    private Long seedRound(AppUserDetails identity, Long job, int number) {
        jdbc.update("INSERT INTO interview_rounds(user_id,job_application_id,round_no) VALUES (?,?,?)", identity.getId(), job, number);
        return jdbc.queryForObject("SELECT id FROM interview_rounds WHERE job_application_id=? AND round_no=?", Long.class, job, number);
    }
}
