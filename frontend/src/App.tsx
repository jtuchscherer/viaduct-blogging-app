import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './contexts/AuthContext'
import Header from './components/Header'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import HomePage from './pages/HomePage'
import CreatePostPage from './pages/CreatePostPage'
import EditPostPage from './pages/EditPostPage'
import PostDetailPage from './pages/PostDetailPage'
import MyPostsPage from './pages/MyPostsPage'
import './App.css'

function App() {
  const { isAuthenticated } = useAuth()

  return (
    <div className="app">
      <Header />
      <main className="container">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route
            path="/login"
            element={isAuthenticated ? <Navigate to="/" /> : <LoginPage />}
          />
          <Route
            path="/register"
            element={isAuthenticated ? <Navigate to="/" /> : <RegisterPage />}
          />
          <Route
            path="/create"
            element={isAuthenticated ? <CreatePostPage /> : <Navigate to="/login" />}
          />
          <Route
            path="/edit/:id"
            element={isAuthenticated ? <EditPostPage /> : <Navigate to="/login" />}
          />
          <Route
            path="/my-posts"
            element={isAuthenticated ? <MyPostsPage /> : <Navigate to="/login" />}
          />
          <Route path="/post/:id" element={<PostDetailPage />} />
        </Routes>
      </main>
    </div>
  )
}

export default App
