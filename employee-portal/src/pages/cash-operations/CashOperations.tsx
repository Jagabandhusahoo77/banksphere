import { Link } from "react-router-dom";
import Card from "@/components/common/Card";
import Icon from "@/components/common/Icon";

export default function CashOperations() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-h2 text-ink-primary">Cash Operations</h1>
        <p className="text-body-sm text-ink-secondary mt-1">Branch cash handling for BankSphere customer accounts.</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Link to="/cash-operations/deposit">
          <Card interactive>
            <div className="flex items-start gap-3">
              <span className="flex items-center justify-center w-10 h-10 rounded-md bg-brand-primary-light text-brand-primary shrink-0">
                <Icon name="arrow-down-left" size={20} />
              </span>
              <div>
                <p className="text-body font-medium text-ink-primary">Cash Deposit</p>
                <p className="text-body-sm text-ink-secondary mt-1">Credit cash into a customer's account.</p>
              </div>
            </div>
          </Card>
        </Link>

        <Link to="/cash-operations/history">
          <Card interactive>
            <div className="flex items-start gap-3">
              <span className="flex items-center justify-center w-10 h-10 rounded-md bg-surface-muted text-ink-secondary shrink-0">
                <Icon name="clock" size={20} />
              </span>
              <div>
                <p className="text-body font-medium text-ink-primary">Deposit History</p>
                <p className="text-body-sm text-ink-secondary mt-1">Your branch's recent cash deposits.</p>
              </div>
            </div>
          </Card>
        </Link>
      </div>
    </div>
  );
}
