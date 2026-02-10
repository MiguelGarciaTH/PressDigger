import { useSearchParams } from "react-router-dom"

export default function ResultsPage() {
  const [params] = useSearchParams()
  const query = params.get("q")

  return (
    <div className="max-w-3xl mx-auto p-6">
      <h2 className="text-xl font-semibold mb-4">
        Results for “{query}”
      </h2>

      <p className="text-gray-500">
        Backend integration comes next.
      </p>
    </div>
  )
}
