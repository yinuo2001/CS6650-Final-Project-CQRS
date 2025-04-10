package org.yinuo.cs6650;

import java.time.LocalDateTime;

public class Post {
  private String postId;
  private String userId;
  private String content;
  private int likeCount;
  private LocalDateTime createdAt;

  public Post(String postId, String userId, String content, int likeCount, LocalDateTime createdAt) {
    this.postId = postId;
    this.userId = userId;
    this.content = content;
    this.likeCount = likeCount;
    this.createdAt = createdAt;
  }

  // Getters and Setters
  public String getPostId() {
    return postId;
  }
  public void setPostId(String postId) {
    this.postId = postId;
  }
  public String getUserId() {
    return userId;
  }
  public void setUserId(String userId) {
    this.userId = userId;
  }
  public String getContent() {
    return content;
  }
  public void setContent(String content) {
    this.content = content;
  }
  public int getLikeCount() {
    return likeCount;
  }
  public void setLikeCount(int likeCount) {
    this.likeCount = likeCount;
  }
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
