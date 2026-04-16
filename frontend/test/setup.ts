import '@testing-library/jest-dom'

// Lexical uses ResizeObserver internally; jsdom doesn't provide it
global.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}
