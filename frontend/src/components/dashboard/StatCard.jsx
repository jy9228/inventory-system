export default function StatCard({ title, value, icon: Icon, color = 'indigo', className }) {
  const colorMap = {
    indigo: 'bg-indigo-50 text-indigo-600 border-indigo-200',
    green: 'bg-green-50 text-green-600 border-green-200',
    amber: 'bg-amber-50 text-amber-600 border-amber-200',
    red: 'bg-red-50 text-red-600 border-red-200',
  };

  return (
    <div className={`bg-white rounded-xl border border-gray-200 p-5 ${className || ''}`}>
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm text-gray-500">{title}</p>
          <p className={`font-bold text-gray-900 mt-1 ${String(value).length > 10 ? 'text-xl' : 'text-2xl'}`}>{value}</p>
        </div>
        <div className={`shrink-0 p-3 rounded-xl border ${colorMap[color]}`}>
          <Icon size={22} />
        </div>
      </div>
    </div>
  );
}
