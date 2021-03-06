/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cn.orz.pascal.blog.dao;

import cn.orz.pascal.blog.entity.Article;
import cn.orz.pascal.blog.entity.Comment;
import java.text.SimpleDateFormat;
import java.util.List;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThan;
import org.junit.*;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ejb.EJB;

/**
 *
 * @author hiro
 */
@RunWith(Arquillian.class)
public class ArticleFacadeTest extends AbstractJPATest {

    public ArticleFacadeTest() {
    }

    @Deployment
    public static Archive<?> createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "test.war").
                addPackage(Article.class.getPackage()).
                addPackage(ArticleFacade.class.getPackage()).
                addAsResource("META-INF/persistence.xml").
                addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }
    @EJB
    ArticleFacade articleFacade;
    @EJB
    CommentFacade commentFacade;

    @Before
    public void preparePersistenceTest() throws Exception {
        clearData(Comment.class);
        clearData(Article.class);
    }

    private void load() throws Exception {
        articleFacade.create(new Article(null, "title1", "contents1"));
        articleFacade.create(new Article(null, "title2", "contents2"));
    }

    @Test
    public void count0_Test() throws Exception {
        assertThat(articleFacade.count(), is(0));
    }

    @Test
    public void save_and_select_Test() throws Exception {
        utx.begin();
        // init and check.
        assertThat(articleFacade.count(), is(0));

        // action.
        articleFacade.create(new Article(null, "title1", "contents1"));
        assertThat(articleFacade.count(), is(1));

        articleFacade.create(new Article(null, "title2", "contents2"));
        assertThat(articleFacade.count(), is(2));

        // expected.
        List<Article> articles = simpleSort(articleFacade.findAll(), "Title");
        assertThat(articles.size(), is(2));
        assertThat(articles.get(0).getTitle(), is("title1"));
        assertThat(articles.get(0).getContents(), is("contents1"));
        assertThat(articles.get(1).getTitle(), is("title2"));
        assertThat(articles.get(1).getContents(), is("contents2"));
        utx.commit();
    }

    @Test
    public void update_Test() throws Exception {
        utx.begin();
        // init and check.
        load();
        List<Article> articles = simpleSort(articleFacade.findAll(), "Title");
        assertThat(articles.size(), is(2));
        assertThat(articles.get(0).getTitle(), is("title1"));
        assertThat(articles.get(1).getTitle(), is("title2"));

        // action
        articles.get(0).setTitle("title3");
        articleFacade.edit(articles.get(0));
        articleFacade.edit(new Article(null, "title4", "contents4"));

        // expected
        List<Article> updatedArticles = simpleSort(articleFacade.findAll(), "Title");
        assertThat(updatedArticles.size(), is(3));
        assertThat(updatedArticles.get(0).getTitle(), is("title2"));
        assertThat(updatedArticles.get(1).getTitle(), is("title3"));
        assertThat(updatedArticles.get(2).getTitle(), is("title4"));
        utx.commit();
    }

    @Test
    public void update_timestamp_Test() throws Exception {
        utx.begin();
        // init and check.
        load();
        List<Article> articles = simpleSort(articleFacade.findAll(), "Id");
        assertThat(articles.size(), is(2));
        assertThat(articles.get(0).getCreatedAt(), is(notNullValue()));
        assertThat(articles.get(0).getUpdatedAt(), is(notNullValue()));
        Long createdAt1 = articles.get(0).getCreatedAt().getTime();
        Long updatedAt1 = articles.get(0).getUpdatedAt().getTime();
        Long createdAt2 = articles.get(1).getCreatedAt().getTime();
        Long updatedAt2 = articles.get(1).getUpdatedAt().getTime();

        // action
        articles.get(0).setTitle("title3");
        articleFacade.edit(articles.get(0));
        articleFacade.edit(new Article(null, "title4", "contents4"));

        // expected
        List<Article> updatedArticles = simpleSort(articleFacade.findAll(), "Id");
        assertThat(updatedArticles.size(), is(3));
        // record1
        assertThat(updatedArticles.get(0).getCreatedAt().getTime(), is(createdAt1));
        assertThat(updatedArticles.get(0).getUpdatedAt().getTime(), is(greaterThan(updatedAt1)));
        // record2
        assertThat(updatedArticles.get(1).getCreatedAt().getTime(), is(createdAt2));
        assertThat(updatedArticles.get(1).getUpdatedAt().getTime(), is(updatedAt2));
        utx.commit();
    }

    @Test
    public void find_recent_articles_Test() throws Exception {
        utx.begin();
        // init and check.
        assertThat(articleFacade.count(), is(0));
        for (int i = 0; i < 100; i++) {
            SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd");

            Article article = new Article(null, "title" + i, "contents" + i);
            article.setCreatedAt(df.parse("2012/04/1"));
            article.setUpdatedAt(df.parse((2012 + i) + "/04/1"));
            articleFacade.create(article);
        }
        assertThat(articleFacade.count(), is(100));

        // expected.
        List<Article> articles = simpleSort(articleFacade.findRecently(10), "Title");
        assertThat(articles.size(), is(10));
        int i = 90;
        for (Article article : articles) {
            assertThat(article.getTitle(), is("title" + (i++)));
        }

        List<Article> articles20 = simpleSort(articleFacade.findRecently(20), "Title");
        assertThat(articles20.size(), is(20));
        int j = 80;
        for (Article article : articles20) {
            assertThat(article.getTitle(), is("title" + (j++)));
        }
        utx.commit();
    }

    @Test
    public void comment_add_Test() throws Exception {
        // init and check.
        utx.begin();
        Article article = new Article(1L, "title1", "contents1");
        articleFacade.create(article);
        Comment comment = new Comment(null, "user2", "comment2");
        comment.setArticle(article);
        commentFacade.create(comment);
        utx.commit();

        // expected.
        utx.begin();

        List<Article> articles = simpleSort(articleFacade.findAll(), "Title");
        assertThat(articles.size(), is(1));
        assertThat(articles.get(0).getId(), is(1L));
        assertThat(articles.get(0).getTitle(), is("title1"));

        Comment comment2 = commentFacade.findAll().get(0);
        assertThat(comment2.getName(), is("user2"));
        assertThat(comment2.getArticleId(), is(1L));
        assertThat(comment2.getArticle().getTitle(), is("title1"));
        assertThat(comment2.getArticle().getId(), is(1L));

        utx.commit();
    }
}