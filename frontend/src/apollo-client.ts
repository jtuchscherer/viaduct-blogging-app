import { ApolloClient, InMemoryCache, createHttpLink, from } from '@apollo/client';
import { setContext } from '@apollo/client/link/context';

// HTTP connection to the GraphQL API
const httpLink = createHttpLink({
  uri: `${import.meta.env.VITE_API_URL}/graphql`,
});

// Middleware to add auth token and schema selection header to requests
const authLink = setContext((_, { headers }) => {
  const token = localStorage.getItem('authToken');
  const authUser = localStorage.getItem('authUser');
  const isAdmin = authUser ? JSON.parse(authUser)?.isAdmin === true : false;

  return {
    headers: {
      ...headers,
      authorization: token ? `Bearer ${token}` : '',
      ...(isAdmin ? { 'X-Schema': 'admin' } : {}),
    }
  };
});

// Create Apollo Client instance
export const client = new ApolloClient({
  link: from([authLink, httpLink]),
  cache: new InMemoryCache(),
  defaultOptions: {
    watchQuery: {
      fetchPolicy: 'cache-and-network',
    },
  },
});
