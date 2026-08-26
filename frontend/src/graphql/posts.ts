import { gql } from '@apollo/client';

/**
 * Every GraphQL document used by the post pages.
 *
 * Shared rather than declared inline because the unit tests need the very same documents the
 * components send: Apollo's MockedProvider matches on the parsed document, so a test-local copy
 * that drifts by a single field stops matching and then fails for a reason that looks nothing
 * like the cause. Three copies of the `GetNode` query had already accumulated by the time drafts
 * added two fields to it.
 */

export const CREATE_BLOG_POST = gql`
  mutation CreatePost($input: CreatePostInput!) {
    createPost(input: $input) {
      id
      title
      content
      status
    }
  }
`;

export const CREATE_CHECKLIST_POST = gql`
  mutation CreateCheckedListPost($input: CreateCheckedListPostInput!) {
    createCheckedListPost(input: $input) {
      id
      title
      status
    }
  }
`;

/**
 * Uses node(id) to support editing both BlogPost and CheckedListPost from the
 * same route. __typename drives the form variant below.
 */
export const GET_NODE_FOR_EDIT = gql`
  query GetNodeForEdit($id: ID!) {
    node(id: $id) {
      __typename
      ... on BlogPost {
        id
        title
        content
        status
        author {
          id
        }
      }
      ... on CheckedListPost {
        id
        title
        description
        status
        author {
          id
        }
      }
    }
  }
`;

/**
 * Both transitions take a bare `ID!` and return the `Post` interface, so one pair of documents
 * covers blog posts and checklists alike.
 */
export const PUBLISH_POST = gql`
  mutation PublishPost($postId: ID!) {
    publishPost(postId: $postId) {
      id
      status
      publishedAt
    }
  }
`;

export const UNPUBLISH_POST = gql`
  mutation UnpublishPost($postId: ID!) {
    unpublishPost(postId: $postId) {
      id
      status
      publishedAt
    }
  }
`;

export const UPDATE_BLOG_POST = gql`
  mutation UpdatePost($input: UpdatePostInput!) {
    updatePost(input: $input) {
      id
      title
      content
    }
  }
`;

export const UPDATE_CHECKLIST_POST = gql`
  mutation UpdateCheckedListPost($input: UpdateCheckedListPostInput!) {
    updateCheckedListPost(input: $input) {
      id
      title
      description
    }
  }
`;

export const GET_MY_POSTS = gql`
  query GetMyPosts {
    myPosts {
      id
      title
      content
      status
      createdAt
      likeCount
      commentCount
    }
    myCheckedListPosts {
      id
      title
      description
      status
      createdAt
      likeCount
      commentCount
    }
  }
`;

/**
 * Uses node(id) so the same route handles both BlogPost and CheckedListPost IDs.
 * __typename drives the rendering branch below.
 */
export const GET_NODE = gql`
  query GetNode($id: ID!) {
    node(id: $id) {
      __typename
      ... on BlogPost {
        id
        title
        content
        status
        publishedAt
        author {
          id
          name
          username
        }
        createdAt
        likeCount
        isLikedByMe
        viewCount
        readTimeMinutes
        comments {
          id
          content
          author {
            id
            name
            username
          }
          createdAt
        }
      }
      ... on CheckedListPost {
        id
        title
        description
        status
        publishedAt
        author {
          id
          name
          username
        }
        createdAt
        likeCount
        isLikedByMe
        viewCount
        readTimeMinutes
        items {
          id
          text
          checked
          position
          createdAt
        }
        comments {
          id
          content
          author {
            id
            name
            username
          }
          createdAt
        }
      }
    }
  }
`;

export const RECORD_POST_VIEW = gql`
  mutation RecordPostView($postId: ID!) {
    recordPostView(postId: $postId)
  }
`;

export const LIKE_POST = gql`
  mutation LikePost($postId: ID!) {
    likePost(postId: $postId) {
      id
      createdAt
    }
  }
`;

export const UNLIKE_POST = gql`
  mutation UnlikePost($postId: ID!) {
    unlikePost(postId: $postId)
  }
`;

export const ADD_COMMENT = gql`
  mutation AddComment($input: CreateCommentInput!) {
    createComment(input: $input) {
      id
      content
      author {
        id
        name
        username
      }
      createdAt
    }
  }
`;

export const DELETE_POST = gql`
  mutation DeletePost($id: ID!) {
    deletePost(id: $id)
  }
`;

export const DELETE_CHECKLIST_POST = gql`
  mutation DeleteCheckedListPost($id: ID!) {
    deleteCheckedListPost(id: $id)
  }
`;

export const TOGGLE_ITEM = gql`
  mutation ToggleCheckedListItem($id: ID!) {
    toggleCheckedListItem(id: $id) {
      id
      checked
    }
  }
`;

export const ADD_ITEM = gql`
  mutation AddCheckedListItem($input: AddCheckedListItemInput!) {
    addCheckedListItem(input: $input) {
      id
      text
      checked
      position
      createdAt
    }
  }
`;

export const DELETE_ITEM = gql`
  mutation DeleteCheckedListItem($id: ID!) {
    deleteCheckedListItem(id: $id)
  }
`;

export const UPDATE_ITEM = gql`
  mutation UpdateCheckedListItem($input: UpdateCheckedListItemInput!) {
    updateCheckedListItem(input: $input) {
      id
      text
      checked
      position
      createdAt
    }
  }
`;
