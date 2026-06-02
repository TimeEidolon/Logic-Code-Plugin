## Heuristics by stack (quick)

### React + TypeScript SPA
- **Entry**: `src/main.tsx` / `src/index.tsx`
- **Providers**: `App.tsx` / `Root.tsx` typically wires Router/Store/I18n
- **Routes**: grep `Routes`, `createBrowserRouter`, `routeConfig`, `useRoutes`
- **State**: grep `createSlice`, `configureStore`, `zustand`, `mobx`, `createContext`, `useReducer`
- **API**: grep `fetch(`, `axios`, `request`, `graphql`, `subscribe`, `EventSource`

### SSE / streaming
Common patterns:
- `AbortController` in a `useRef`
- chunk callback appends to a buffer
- “done” callback promotes buffer into messages/state
- stop/cancel should: abort + bump generation + clear buffer + settle UI state

### What “good” diagrams look like

#### Architecture (module boundaries)
Prefer 6–12 boxes max:
- `pages/` (feature entry points)
- `modules/` (feature components)
- `common/` (shared UI + services)
- `store/` (state)
- `api/` (network)
- `utils/` (helpers)

#### Dataflow (runtime)
Show arrows for:
UI event → action/service → API/SSE → reducer/state → render

