import { Link } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import './Header.css'

export default function Header() {
  const { user, isAuthenticated, logout } = useAuth()

  return (
    <header className="header">
      <div className="container header-content">
        <Link to="/" className="logo">
          <h1>Viaduct Blog</h1>
        </Link>

        <nav className="nav">
          <Link to="/">Home</Link>

          {isAuthenticated ? (
            <>
              <Link to="/my-posts">My Posts</Link>
              <Link to="/create" className="btn-primary">
                New Post
              </Link>
              <div className="user-menu">
                <span className="user-name">{user?.name}</span>
                <button onClick={logout} className="btn-secondary">
                  Logout
                </button>
              </div>
            </>
          ) : (
            <>
              <Link to="/login">Login</Link>
              <Link to="/register" className="btn-primary">
                Register
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  )
}
