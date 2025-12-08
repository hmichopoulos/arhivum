# Archivum UI

React-based web interface for the Archivum file organization system.

## Tech Stack

- **React 18** - UI framework
- **TypeScript** - Type safety
- **Vite** - Build tool and dev server
- **Tailwind CSS** - Styling
- **React Router** - Client-side routing
- **TanStack Query (React Query)** - Data fetching and caching

## Project Structure

```
archivum-ui/
├── src/
│   ├── api/              # API client functions
│   │   ├── sources.ts
│   │   ├── files.ts
│   │   └── code-projects.ts
│   ├── components/       # Reusable UI components
│   │   ├── Layout.tsx
│   │   ├── SourceCard.tsx
│   │   ├── FileTable.tsx
│   │   └── ...
│   ├── hooks/           # React Query hooks
│   │   ├── useSources.ts
│   │   ├── useFiles.ts
│   │   └── useCodeProjects.ts
│   ├── pages/           # Page components
│   │   ├── SourcesListPage.tsx
│   │   ├── SourceDetailsPage.tsx
│   │   └── CodeProjectsPage.tsx
│   ├── types/           # TypeScript type definitions
│   │   ├── source.ts
│   │   ├── file.ts
│   │   └── code-project.ts
│   ├── App.tsx          # Main app with routing
│   ├── main.tsx         # Entry point
│   └── index.css        # Global styles
├── index.html           # HTML template
├── package.json         # Dependencies
├── vite.config.ts       # Vite configuration
├── tailwind.config.js   # Tailwind configuration
└── tsconfig.json        # TypeScript configuration
```

## Setup Instructions

### Prerequisites

- Node.js 18+ and npm
- Backend server running on `http://localhost:8080`

### Installation

1. Navigate to the UI directory:
   ```bash
   cd archivum-ui
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

### Running the Development Server

Start the dev server with hot reload:

```bash
npm run dev
```

The UI will be available at **http://localhost:3000**

API requests to `/api/*` are automatically proxied to `http://localhost:8080`

### Building for Production

Build the production bundle:

```bash
npm run build
```

Preview the production build:

```bash
npm run preview
```

## Available Pages

### Sources (`/sources`)
- View all scanned sources (disks, cloud accounts)
- Filter by status, type, and search
- See scan statistics (total sources, files, size)
- Click on a source to view details

### Source Details (`/sources/:id`)
- View detailed information about a source
- Browse all files in the source
- Filter files by extension or duplicates
- Sort files by name, size, status, or date
- View physical device information

### Code Projects (`/code-projects`)
- Browse detected code projects
- View duplicates
- See project statistics

## API Integration

The UI automatically proxies API requests through Vite's dev server:

- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- API calls to `/api/*` are forwarded to the backend

### Example API Call

```typescript
// In src/api/sources.ts
const response = await fetch('/api/sources');
// This actually calls: http://localhost:8080/api/sources
```

## Development

### Adding a New Page

1. Create the page component in `src/pages/`:
   ```typescript
   export function MyNewPage() {
     return <div>My New Page</div>;
   }
   ```

2. Add the route in `src/App.tsx`:
   ```typescript
   <Route path="/my-new-page" element={<MyNewPage />} />
   ```

3. Add navigation link in `src/components/Layout.tsx`

### Adding a New API Endpoint

1. Add TypeScript types in `src/types/`
2. Create API client in `src/api/`
3. Create React Query hook in `src/hooks/`
4. Use the hook in your component

### Styling

This project uses **Tailwind CSS** for styling. All styles are utility-based:

```typescript
<div className="bg-white rounded-lg shadow-sm p-4">
  <h1 className="text-2xl font-bold text-gray-900">Title</h1>
</div>
```

## Troubleshooting

### Port 3000 already in use

Change the port in `vite.config.ts`:

```typescript
server: {
  port: 3001  // Change to any available port
}
```

### API requests failing

1. Ensure backend server is running on `http://localhost:8080`
2. Check browser console for CORS errors
3. Verify proxy configuration in `vite.config.ts`

### Hot reload not working

1. Stop the dev server
2. Delete `node_modules` and `package-lock.json`
3. Run `npm install` again
4. Restart with `npm run dev`

## Next Steps

Future enhancements:

- Add authentication/authorization
- Implement file details modal
- Add batch operations (delete, move)
- Real-time updates via WebSocket
- File preview functionality
- Advanced search and filtering
- Export/import functionality
