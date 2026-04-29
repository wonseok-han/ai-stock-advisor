package com.nowini.feedback.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "feedback")
public class FeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    private String email;
    private String type;
    private String subject;
    private String body;
    private String url;

    @Column(name = "user_agent")
    private String userAgent;

    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected FeedbackEntity() {}

    public FeedbackEntity(UUID userId, String email, String type, String subject,
                          String body, String url, String userAgent) {
        this.userId = userId;
        this.email = email;
        this.type = type;
        this.subject = subject;
        this.body = body;
        this.url = url;
        this.userAgent = userAgent;
        this.status = "open";
    }

    public Long getId() { return id; }
    public String getStatus() { return status; }
    public String getType() { return type; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getEmail() { return email; }
}
